include_defs('//bucklets/gerrit_plugin.bucklet')

gerrit_plugin(
  name = 'quota',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-PluginName: quota',
    'Gerrit-Module: com.googlesource.gerrit.plugins.quota.Module',
  ],
  provided_deps = [
    '//lib/commons:lang',
    '//lib/commons:dbcp',
    '//lib/commons:pool',
  ]
)

# this is required for bucklets/tools/eclipse/project.py to work
# not sure, if this does something useful in standalone context
java_library(
  name = 'classpath',
  deps = [':quota__plugin'],
)

java_test(
  name = 'quota_tests',
  srcs = glob(['src/test/java/**/*.java']),
  labels = ['quota-plugin'],
  deps = GERRIT_PLUGIN_API + [
    ':quota__plugin',
    '//lib:junit',
    '//lib/easymock:easymock',
    '//lib/log:log4j',
    '//lib/log:impl_log4j',
  ],
  source_under_test = [':quota__plugin'],
)
