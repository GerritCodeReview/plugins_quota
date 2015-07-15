@PLUGIN@ - /projects/ REST API
==============================

This page describes the REST endpoints that are added by the @PLUGIN@
plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

<a id="project-endpoints"> Quota Endpoints
------------------------------------------

### <a id="get-quota"> Get Quota
_GET /projects/\{project\}/@PLUGIN@~quota/_

Get quota for a project.

#### Request

```
  GET /projects/customerX%2Fcalculator/@PLUGIN@~quota/ HTTP/1.0
```

As response a [QuotaInfo](#quota-info) entity is returned
that describes the projects quota.

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json;charset=UTF-8

  )]}'
  {
    "repo_size": 386,
    "max_repo_size": 1048576,
    "namespace": {
      "name": "customerX/*",
      "total_size": 1874,
      "max_total_size": 10485760
    }
  }
```

### <a id="get-quotas"> Get Quotas for Multiple Projects
_GET /config/server/@PLUGIN@~quota/_

Get quota for the projects accessible by the caller.

#### Request

```
  GET /config/server/@PLUGIN@~quota/ HTTP/1.0
```

As result a map is returned that maps the project names to
[QuotaInfo](#quota-info) entries. The entries in the map are sorted
by project name.

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json;charset=UTF-8

  )]}'
  {
    "external/bison": {
      "repo_size": 386,
      "max_repo_size": 1048576,
      "namespace": {
        "name": "external/*",
        "total_size": 1874,
        "max_total_size": 10485760
      }
    },
    "internal/gcc": {
      "repo_size": 386
    }
  }
```

#### Prefix(p) Option

Limit the results to those projects that start with the specified
prefix.

##### Request

```
  GET /config/server/@PLUGIN@~quota/?p=external%2F HTTP/1.0
```

As result a map is returned that maps the project names to
[QuotaInfo](#quota-info) entries. The entries in the map are sorted
by project name.

##### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json;charset=UTF-8

  )]}'
  {
    "external/bison": {
      "repo_size": 386,
      "max_repo_size": 1048576,
      "namespace": {
        "name": "external/*",
        "total_size": 1874,
        "max_total_size": 10485760
      }
    }
  }
```

<a id="json-entities">JSON Entities
-----------------------------------

### <a id="quota-info"></a>QuotaInfo

The `QuotaInfo` entity contains information about a project's quota.
It has the following fields:

* _repo\_size_: The disk space, in bytes, that is used by this project's Git repository
* _max\_repo\_size_: The max allowed size of this project's Git repositoriy on the disk.
* _namespace_: [NamespaceInfo](#namespace-info)


### <a id="namespace-info"></a>NamespaceInfo

The 'NamespaceInfo' entity contains the quota information for the whole namespace.
This means that the sum of sizes of all repositories under that namespace is not
allowed to exceed the namespace quota. It has the following fields:

* _name_: the namepspace name
* _total\_size_: the total size of all repositories under this namespace
* _max\_total\_size_: the maximum allowed total size of all repositories under this
  namespace
