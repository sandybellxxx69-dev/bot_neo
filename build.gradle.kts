// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  id("org.jetbrains.kotlin.android") version "1.9.22" apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}
