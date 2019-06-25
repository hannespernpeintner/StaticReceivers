package de.hanno.kotlin.plugins

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.MethodInfoList
import io.github.classgraph.ScanResult
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.lang.IllegalStateException
import java.net.URLClassLoader
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import kotlin.reflect.jvm.internal.impl.name.FqName


open class GenerateFunctionWrappersTask: DefaultTask() {

    @Input
    val configurations = setOf("compile", "runtime")

    val outputFolder = project.buildDir.resolve("staticmethods")

    @Input
    val packageWhiteList = arrayOf("org.apache.commons.*")
    @Input
    val packageBlackList = arrayOf("org.gradle.*")

    @TaskAction
    fun execute() {
        val dependencyFiles = configurations.flatMap { project.configurations.getByName(it).resolvedConfiguration.files }.toSet()
        logger.info("Scanning ${dependencyFiles.size} files:")
        dependencyFiles.forEach {
            logger.info(it.absolutePath)
        }
        dependencyFiles.scanAllClassesAndWriteOutWrapperFunctions(outputFolder, packageWhiteList, packageBlackList)
    }
}

class StaticReceiversPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.tasks.create("generateReceiverFunctions", GenerateFunctionWrappersTask::class.java)
    }

//     TODO: Do I need this somewhen?
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

data class ClassInfoFunSpecsPair(val classInfo: ClassInfo, val funSpecs: List<FunSpec>)

private fun Set<File>.scanAllClassesAndWriteOutWrapperFunctions(outputFolder: File, whiteListPackages: Array<String>, blackListPackages: Array<String>) {
    scanClassPath(whiteListPackages, blackListPackages)?.use { scanResult ->
        val classesAndFunSpecs = scanResult.generateFunSpecsForClasses()
        classesAndFunSpecs.writeToSourceFilesInOutputDir(outputFolder, "de.hanno.generated")
    }
}

private fun List<ClassInfoFunSpecsPair>.writeToSourceFilesInOutputDir(outputFolder: File, targetPackageName: String) {
    for (classAndFunSpec in distinctBy { it.classInfo.name }.filter { it.funSpecs.isNotEmpty() }) {
        val fileSpecBuilder = FileSpec.builder(targetPackageName, classAndFunSpec.classInfo.simpleName)
        fileSpecBuilder.addType(
            TypeSpec.objectBuilder(classAndFunSpec.classInfo.simpleName).apply {
                for (funSpec in classAndFunSpec.funSpecs.distinct()) {
                    addFunction(funSpec).build()
                }
            }.build()
        )
        val fileSpec = fileSpecBuilder.build()
        fileSpec.writeTo(outputFolder)
    }
}

fun ClassInfo.retrieveFunSpecs(staticMethodsWithParams: MethodInfoList): List<FunSpec> = try {
    val loadedClass = loadClass()
    loadedClass.retrieveFunSpecs(staticMethodsWithParams, this)
} catch (e: IllegalArgumentException) {
    if (e.cause !is NoClassDefFoundError) {
        println("OMFG It explodes, expected cause to be NoClassDefFoundError, but was $e")
    }
    emptyList()
}

private fun ScanResult.generateFunSpecsForClasses(): List<ClassInfoFunSpecsPair> {
    return allClasses.map { clazz ->
        val staticMethodsWithParams = clazz.methodInfo.filter { it.isStatic }.filter { it.parameterInfo.isNotEmpty() }

        val funSpecsForClass = clazz.retrieveFunSpecs(staticMethodsWithParams)
        ClassInfoFunSpecsPair(clazz, funSpecsForClass)
    }
}

private fun Class<*>.retrieveFunSpecs(staticMethodsWithParams: MethodInfoList, clazz: ClassInfo): List<FunSpec> {
    return staticMethodsWithParams.mapNotNull { info ->
        try {
            val loadedMethod = methods.first { it.name == info.name && it.parameterCount == info.parameterInfo.size }
            val firstParam = loadedMethod.parameters.first()
            val restParams = loadedMethod.parameters.toList()
                    .subList(1, loadedMethod.parameterCount)
            val restParamsString = if(restParams.isEmpty()) "" else ", " + restParams
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
}

private fun Set<File>.scanClassPath(whiteListPackage: Array<String>, blackListPackages: Array<String>): ScanResult? {
    val classLoader = URLClassLoader(map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getSystemClassLoader())

    return ClassGraph()
            .ignoreParentClassLoaders()
            .verbose()
            .enableMethodInfo()
            .whitelistPackages(*whiteListPackage)
            .blacklistPackages(*blackListPackages)
            .addClassLoader(classLoader)
            .scan()
}