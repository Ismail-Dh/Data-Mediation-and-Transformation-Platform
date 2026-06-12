$annotation = '@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)'
$import = 'import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;'

# Liste des fichiers à modifier
$files = @(
    # Controllers
    ".\backend\src\main\java\com\miniESB\controller\AdminTemplateController.java",
    ".\backend\src\main\java\com\miniESB\controller\AuditLogController.java",
    ".\backend\src\main\java\com\miniESB\controller\AuthController.java",
    ".\backend\src\main\java\com\miniESB\controller\BuildMonitorController.java",
    ".\backend\src\main\java\com\miniESB\controller\DeveloperTemplateController.java",
    ".\backend\src\main\java\com\miniESB\controller\DockerImageController.java",
    ".\backend\src\main\java\com\miniESB\controller\GlobalValidationRuleController.java",
    ".\backend\src\main\java\com\miniESB\controller\MappingRuleController.java",
    ".\backend\src\main\java\com\miniESB\controller\MonitoringController.java",
    ".\backend\src\main\java\com\miniESB\controller\PayloadController.java",
    ".\backend\src\main\java\com\miniESB\controller\PipelineController.java",
    ".\backend\src\main\java\com\miniESB\controller\PipelineFieldController.java",
    ".\backend\src\main\java\com\miniESB\controller\PipelineValidationRuleController.java",
    ".\backend\src\main\java\com\miniESB\controller\ProviderController.java",
    ".\backend\src\main\java\com\miniESB\controller\SandboxController.java",
    ".\backend\src\main\java\com\miniESB\controller\UserController.java",
    ".\backend\src\main\java\com\miniESB\controller\ValidationPreviewController.java",
    # Services impl
    ".\backend\src\main\java\com\miniESB\service\impl\AuditLogServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\GlobalValidationRuleServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\MappingServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\MonitoringServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\PayloadServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\PipelineFieldServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\PipelineServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\PipelineValidationRuleServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\ProviderServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\SandboxServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\TemplateServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\UserServiceImpl.java",
    ".\backend\src\main\java\com\miniESB\service\impl\ValidationPreviewServiceImpl.java",
    # Security
    ".\backend\src\main\java\com\miniESB\security\UserDetailsServiceImpl.java"
)

foreach ($file in $files) {
    if (!(Test-Path $file)) {
        Write-Host "SKIP (not found): $file"
        continue
    }

    $content = Get-Content $file -Raw

    # Ajoute l'import si pas déjà présent
    if ($content -notmatch 'ConditionalOnProperty') {
        # Ajoute import après le package
        $content = $content -replace '(package com\.miniESB[^;]+;)', "`$1`n$import"

        # Ajoute l'annotation avant @RestController, @Controller, @Service, @Component
        $content = $content -replace '(@(?:RestController|Controller|Service|Component))', "$annotation`n`$1"

        Set-Content $file $content -NoNewline
        Write-Host "OK: $file"
    } else {
        Write-Host "ALREADY DONE: $file"
    }
}