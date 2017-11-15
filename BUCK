include_defs('//bucklets/gerrit_plugin.bucklet')

gerrit_plugin(
  name = 'quota',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-PluginName: quota',
    'Gerrit-Module: com.googlesource.gerrit.plugins.quota.Module',
    'Gerrit-HttpModule: com.googlesource.gerrit.plugins.quota.HttpModule',
  ],
  provided_deps = [
    '//lib/commons:lang',
  ]
)

TEST_DEPS = GERRIT_PLUGIN_API + GERRIT_TESTS + [
  ':quota__plugin'
]

java_library(
  name = 'classpath',
  deps = TEST_DEPS,
)

java_test(
  name = 'quota_tests',
  srcs = glob(['src/test/java/**/*.java']),
  labels = ['quota'],
  deps = TEST_DEPS,
)
