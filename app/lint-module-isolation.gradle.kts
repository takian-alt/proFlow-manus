/**
 * Custom Lint check for launcher module isolation.
 *
 * Ensures the launcher module (presentation/launcher/) has no compile-time dependency
 * on presentation/ ViewModels or UseCases from other features.
 *
 * Requirements: 21.5
 */

tasks.register<Exec>("checkLauncherModuleIsolation") {
    group = "verification"
    description = "Verify launcher module has no forbidden dependencies on other presentation modules"

    commandLine("bash", "../scripts/check-launcher-isolation.sh")
    workingDir = projectDir
}

// Run this check as part of the standard check task
tasks.named("check") {
    dependsOn("checkLauncherModuleIsolation")
}
