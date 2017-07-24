This plugin allows to enforce quotas in Gerrit.

To protect a Gerrit installation it makes sense to limit the resources
that a project or group can consume. To do this a Gerrit administrator
can use this plugin to define quotas on project namespaces.

The @PLUGIN@ plugin supports the following quotas:

* Maximum number of projects in a namespace
* The maximum total file size of a repository in a namespace

The measured repository sizes can be published periodically to registered
UsageDataPublishedListeners.

If the `enableDiskQuota` configuration option in `gerrit.config` is set to
false, then repository sizes are no longer tracked or enforced.
By default, `enableDiskQuota` is set to true.

The @PLUGIN@ plugin supports the following  rate limits:

* `uploadpack` requests which are executed when a client runs a fetch command.

Rate limits define the maximum request rate for users in a given group
for a given request type.