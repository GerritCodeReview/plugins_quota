Build
=====

This plugin is built with Bazel.

Bazel
----

Clone (or link) this plugin to the `plugins` directory of Gerrit's source tree.

Build the plugin from Gerrit's root directory:

```
  bazel build plugins/quota
```

The output is created in

```
  bazel-genfiles/plugins/quota/quota.jar
```

This project can be imported into the Eclipse IDE.
Add the plugin name to the `CUSTOM_PLUGINS` set in Gerrit's
`tools/bzl/plugins.bzl` and execute:

```
  ./tools/eclipse/project.py
```

To execute the tests run:

```
  bazel test plugins/quota:quota_tests
```
