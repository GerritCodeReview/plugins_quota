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

