package de.hanno.kotlin.plugins

import com.squareup.kotlinpoet.*
import io.github.classgraph.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.net.URLClassLoader
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import kotlin.reflect.jvm.internal.impl.name.FqName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.lang.IllegalArgumentException


class StaticReceiversPlugin: Plugin<Project> {
    override fun apply(project: Project) {

        val compileDependencies = project.configurations.getByName("compile").resolvedConfiguration
        val runtimeDependencies = project.configurations.getByName("runtime").resolvedConfiguration
        val dependencyFiles = compileDependencies.files + runtimeDependencies.files

        project.logger.info("Found dependencies: $dependencyFiles")

        val classLoader = URLClassLoader(dependencyFiles.map { it.toURI().toURL() }.toTypedArray())

        ClassGraph()
            .verbose()
            .enableMethodInfo()
            .whitelistPackages("org.apache.commons.*")
//            .blacklistPackages("org.gradle.*")
            .addClassLoader(classLoader)
            .scan()?.use { scanResult ->
                var allMethods = ""
                val classesAndFunSpecs = scanResult.allClasses.map { clazz ->
                    val methodInfo = clazz.methodInfo
                    val staticMethods = methodInfo.filter { it.isStatic }.filter { it.parameterInfo.isNotEmpty() }

                    val resultStrings = staticMethods.fold("") { current, method -> current + "\n" +method.name }
                    allMethods += resultStrings
                    val funSpecsForClass = try {
                        val loadedClass = clazz.loadClass()
                        staticMethods.mapNotNull { info ->
                            try {
                                val loadedMethod = loadedClass.methods.first { it.name == info.name && it.parameterCount == info.parameterInfo.size }
                                val firstParam = loadedMethod.parameters.first()
                                val restParams = loadedMethod.parameters.toList()
                                        .subList(1, loadedMethod.parameterCount)
                                val restParamsString = restParams
                                        .joinToString(", ") { it.name }

                                val factoriesFun = FunSpec.builder(loadedMethod.name)
                                        .receiver(firstParam.parameterizedType)
                                        .returns(loadedMethod.genericReturnType)
                                        .addParameters(restParams.map { ParameterSpec.builder(it.name, it.parameterizedType).build() })
                                        .addStatement("return ${clazz.name}.${loadedMethod.name}(this$restParamsString)")
                                        .build()

                                factoriesFun
                            } catch (e: NoClassDefFoundError) {
                                null
                            }
                        }
                    } catch (e: IllegalArgumentException) {
                        if (e.cause !is NoClassDefFoundError) {
                            println("OMFG It explodes, expected cause to be NoClassDefFoundError, but was $e")
                        }
                        emptyList<FunSpec>()
                    }
                    Pair(clazz, funSpecsForClass)
                }

                val folder = project.buildDir.resolve("staticmethods")
                val staticMethodsOutputFile = folder.resolve("staticmethods.txt")
                println(staticMethodsOutputFile.parentFile.mkdirs())
                staticMethodsOutputFile.writeText(allMethods)


                for (classAndFunSpec in classesAndFunSpecs.distinctBy { it.first }.filter { it.second.isNotEmpty() }) {
                    val fileSpecBuilder = FileSpec.builder("de.hanno.generated", classAndFunSpec.first.simpleName)
                    for (funSpec in classAndFunSpec.second) {
                        fileSpecBuilder.addFunction(funSpec).build()
                    }
                    val fileSpec = fileSpecBuilder.build()
                    fileSpec.writeTo(staticMethodsOutputFile.parentFile)
                }
            }

    }

    private fun TypeName.javaToKotlinType(): TypeName {
        return if (this is ParameterizedTypeName) {
            val className = rawType.javaToKotlinType() as ClassName
            className.parameterizedBy(*typeArguments.map { it.javaToKotlinType() }.toTypedArray())
        } else {
            val className = JavaToKotlinClassMap.INSTANCE
                    .mapJavaToKotlin(FqName(toString()))?.asSingleFqName()?.asString()
            if (className == null) this
            else ClassName.bestGuess(className)
        }
    }

}
