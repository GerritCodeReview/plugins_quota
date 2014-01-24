include_defs('//lib/maven.defs')

API_VERSION = '2.9-SNAPSHOT'
REPO = MAVEN_LOCAL

gerrit_plugin(
  name = 'quota',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-PluginName: quota',
    'Gerrit-Module: com.googlesource.gerrit.plugins.quota.Module',
  ]
)

maven_jar(
  name = 'plugin-lib',
  id = 'com.google.gerrit:gerrit-plugin-api:' + API_VERSION,
  repository = REPO,
  license = 'Apache2.0',
)
