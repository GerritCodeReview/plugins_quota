This plugin allows to enforce quotas in Gerrit.

To protect a Gerrit installation it makes sense to limit the resources
that a project or group can consume. To do this a Gerrit administrator
can use this plugin to define quotas on project namespaces.

The @PLUGIN@ plugin supports the following quotas:

* Maximum number of projects in a namespace
* The maximum total file size of a repository in a namespace

The measured repository sizes can be published periodically to registered
UsageDataPublishedListeners.
