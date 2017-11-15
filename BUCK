include_defs('//bucklets/gerrit_plugin.bucklet')
include_defs('//bucklets/maven_jar.bucklet')

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
  ':quota__plugin',
  ':mockito'
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

maven_jar(
  name = 'mockito',
  id = 'org.mockito:mockito-core:2.7.21',
  sha1 = '23e9f7bfb9717e849a05b84c29ee3ac723f1a653',
  license = 'DO_NOT_DISTRIBUTE',
  deps = [
    ':byte-buddy',
    ':objenesis',
  ],
)

maven_jar(
  name = 'byte-buddy',
  id = 'net.bytebuddy:byte-buddy:1.6.11',
  sha1 = '8a8f9409e27f1d62c909c7eef2aa7b3a580b4901',
  license = 'DO_NOT_DISTRIBUTE',
  attach_source = False,
)

maven_jar(
  name = 'objenesis',
  id = 'org.objenesis:objenesis:2.5',
  sha1 = '612ecb799912ccf77cba9b3ed8c813da086076e9',
  license = 'DO_NOT_DISTRIBUTE',
  attach_source = False,
)
