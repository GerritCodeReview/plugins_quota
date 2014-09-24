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
  deps = [
    ':quota__plugin',
    '//lib:junit',
    '//gerrit-reviewdb:server',
    '//lib/jgit:jgit',
    '//lib/easymock:easymock',
    '//gerrit-extension-api:api',
    '//gerrit-server:server',
    '//lib/guice:guice',
    '//lib:gwtorm',
    '//lib:h2',
  ],
  source_under_test = [':quota__plugin'],
)
