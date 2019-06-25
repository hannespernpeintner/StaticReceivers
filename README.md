Every now and then, one wants to use static Java methods from Kotlin as if
they were extension functions, with the first parameter as a receiver.
This is a feature provided by other languages out of the box
(Xtend for example https://www.eclipse.org/xtend/documentation/202_xtend_classes_members.html#extension-methods).
Besides implementing a Kotlin compiler extension and IDE support directly, which
would be the correct way to provide the feature, I tried to do the following:

* Have a gradle plugin and a configured generated sources folder
* Have the gradle plugin scan configurations (for example compile and runtime)
* Resolve the corresponding files
* Filter for classes and then for static methods that have at least one parameter
* Load the classes
* Use Kotlinpoet to generate Kotlin extension functions for all static methods in objects for alle classes
  and just call the static Java function with receiver as parameter

Result tldr:

Doesnt work. It works well (use the test project with the configured composite build)
in general and generates output like

```kotlin
package de.hanno.generated

import java.util.Collection
import org.apache.commons.collections.Predicate

object AllPredicate {
  fun Collection.getInstance(): Predicate =
      org.apache.commons.collections.functors.AllPredicate.getInstance(this)
}
```

But as you can see, Collection is a raw type. That's because the type parameter is indeed missing
from AllPedicate.getInstance's parameter... it would be very complicated to add missing types
in case of raw parameters for all parameters and parameters that become receivers.

Additionally, there is no correct mapping between Java and corresponding Kotlin types. That means
String from Java is not translated into kotlin.String for the Kotlin wrapper. This gives a warning.

All in all, I think the approach is not worth the effort of saving a few key strokes, I think
it's better to implement wrappers on demand ... or even better, I'll write a compiler extension :)
