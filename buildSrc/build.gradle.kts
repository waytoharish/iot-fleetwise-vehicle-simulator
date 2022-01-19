// Setup the "brazilGradle" extension to use in the "buildscript {}" block.
val brazilGradleRepo: String? by project
val brazilGradleName: String? by project
if (brazilGradleRepo != null && brazilGradleName != null) {
  repositories {
    maven {
      url = uri(brazilGradleRepo as String)
    }
  }
  dependencies {
    "runtimeOnly"(brazilGradleName as String)
  }
}
