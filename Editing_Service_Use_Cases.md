## Editing Services Use Cases

This is part of defining the API between the Editing Client (EC) and the Editing Service (ES). We work through some uses  cases starting simple and then getting more elaborate. We assume the idea of an RDFPatch, RDFPatchLog (a sequence of RDFPatches) and json-ld serialization of resources.

### Edit one existing resource
In this case the user wants to make some changes to an existing resource:

- EC gets the resource ID of the resource to be edited from a search that the user then selects from or perhaps the user "knows" the ID and just types it in or maybe the EC provides some bookmark feature.

- EC then makes a `GET http://purl.bdrc.io/graph/theID` request for a json-ld serialization of the resource.

- once the resource is received then the EC populates the editing UI, making ready for the user to review and edit the resource - edits may involve adding information, deleting or updating existing information

- upon the first edit action by the user, EC creates a new `RDFPatch` to record the user's changes. This includes creating an `H id <patchID> .` for the patch, an `H graph <...> .` (or some other encoding), and `H message "..." .` that describes the work to be done in the patch (EC solicits this from the user.)

- the user makes edits involving adding, deleting or updating existing information, and the EC records each action as a sequence of RDFPatch `A` and `D` steps. Each `A` or `D` consists of a quad of a `subject property object graph`. In this case the `graph` is that for the resource being edited, `http://purl.bdrc.io/graph/theID`.

- once the user is finished with their updates and signals to EC that the update is completed then EC performs a

```
    POST http://purl.bdrc.io/sandbox/patchID?apply
        header includes a shortname for the RDFPatchLog that will be created; user id and authorization info
        the patch payload
```
- ES creates an RDFPatchLog containing the patch and saves it in a patch log store, applies the patch - which consists of updating the dataset on Fuseki and the appropriate local git repo and perhaps pushes to the public repo. Upon a successful apply ES responds w/ 

    - `201`(created) or 
    - `202`(accepted) - to be decided. 
    
    in either case the patch log short name and id are returned along w/ other info tbd.

	On failure ES responds with one of:

    - `400`(bad request) - syntax error and so on, 
    - `403`(forbidden - not authorized), 
    - `408`(request timeout - waiting for the complete request)
    - `409`(conflict) - patch is in conflict with the current state of the resource (an intervening update occurred perhaps). This error would also be returned in the event that the `patchID` has already been used (shouldn't occur)
    - `410`(gone) - the resource being updated by the patch is no longer available
    - `500`(internal server error) - unexpected internal failure
    - `503`(service unavailable) - temporary overload, planned maintenance, Fuseki or git repos not available,e tc. 

    with appropriate error information

This completes this use case.

### Create one new resource
The wants to add a new Work, Person, etc, with no references to any existing resources.

- The user requests EC to setup up the editing UI for creating a new resource of a specified type. EC must create a new resource ID, `theID` - the current idea is that EC will generate a UUID and prefixed with the usual `W`, `P`, etc depending on the type.

- upon the first edit action by the user, EC creates a new `RDFPatch` as above, except that `H create <. . . > .` is used instead of `H graph ... .`.

- the user makes edits involving adding, deleting or updating existing information, and the EC records each action in the patch. Each edit uses the same graph term as above, `http://purl.bdrc.io/graph/theID`.

- once the user is finished with their updates and signals to EC that the update is completed then, as [above](#edit-one-existing-resource), EC performs:

```
    POST http://purl.bdrc.io/sandbox/patchID?apply
        header includes a short name for the RDFPatchLog that will be created; user id and authorization info
        the patch payload
```

- ES creates an RDFPatchLog containing the patch and saves it in a patch log store, applies the patch after checking that the supplied resource ID, `theID`, is not in use with responses as above. ES will also need to add `adm:GetInfo` for the new resource.

### Create a resource referring to an existing resource

The user may want to create a new Work by an already existing Person and make a reference to the Person as a _Main Author_, `R0ER0019`. This use case proceeds as w/ [create one...](#create-one-new-resource) with the addition that EC must provide a method for the user to identify the Person resource that is to be referred to. This may be done w/ a _live search_ of Persons that allows the user to pick out the appropriate Person resource.

In this use case, there is no need for EC to get the Person resource since the user doesn't have any updates to make to the Person resource.

As a general comment, EC will need to mint a UUID for the `:AgentAsCreator` resource that will be added to the graph begin constructed. There are many instances during editing where id minting will need to occur for creating named resources such as `:WorkTitle`, `:AgentAsCreator`, `:WorkEvent`, `:Note`, and others depending the type of the resource.

### Create a resource and view a referred to resource

This case proceeds as [above](#create-a-resource-referring-to-an-existing-resource) except that here EC will be signalled by the user that the referred to resource needs to be retrieved and a new window or tab made available to allow the user to review the referred to resource; otherwise, much the same.

### Create a resource and update a referred to resource

This proceeds as just discussed except that the user signals EC that they are updating the referred to resource by taking a UI action that adds, deletes, or updates information about the referred to resource via the tab or window displaying the referred to resource.

In this instance EC will add `H graph <http://purl.bdrc.io/graph/referredID> .` to the patch and the various edit actions w.r.t. the _referred to_ resource will include this graph id, like:

```
    A <http://purl.bdrc.io/resource/referredId> <http://purl.bdrc.io/ontology/core/seeAlso> <http://purl.bdrc.io/resource/theId> <http://purl.bdrc.io/graph/referredID> .
```
for this case the patch will look schematically like:

```
    H id <patchId> .
    H name "addingNewFPL" .
    H message "adding a new FPL work just received" .
    H create <http://purl.bdrc.io/graph/theID> .
    H graph <http://purl.bdrc.io/graph/referredID> .
    TX .
    A <subject1> <property1> <object1> <http://purl.bdrc.io/graph/theID> .
    . . .
    D <subjectN> <propertyN> <objectN> <http://purl.bdrc.io/graph/referredID> .
    A <subjectN> <propertyN> <objectN+1> <http://purl.bdrc.io/graph/referredID> .
    . . .
    TC .
```
EC will submit the patch as in previous cases:

```
    POST http://purl.bdrc.io/sandbox/patchID?apply
        header includes a shortname for the RDFPatchLog that will be created; user id and authorization info
        the patch payload
```

### Editing in several sessions

The user proceeds to create, refer, and update some set of resources as in previous cases but before they are finished the user wants to take a break, maybe go home for the day and work from home or the next day back at their office and so on. For this case the user signals EC that they want to _save_ or _stash_ their work up to this point and EC performs:

```
    POST http://purl.bdrc.io/sandbox/patchID?stash
        header includes a shortname for the RDFPatchLog that will be created; user id and authorization info
        the patch payload
```
when ES receives the request a new patch log is created and saved in a sandbox or staging area for future reference.

Later the user wants to resume their task represented so far by the stashed patch. In order to resume the patch log the user will need to be able to signal EC as to which patch log to resume. Generally, this will require EC to request a list of stashed patch logs associated with the user - they may have several tasks in progress:

```
    GET http://purl.bdrc.io/sandbox
        user id and authorization info
```

Not mentioned until now is that the requests from EC to ES contain authorization and user ID information in the requests. This permits ES to validate user permissions and so on and allows to group patch logs by user ID.

Once the user is provided w/ a list of stashed patches or via some bookmark mechanism, etc then EC will request the patch log, `PatchLogID` from ES:

```
    GET http://purl.bdrc.io/sandbox/patchLogID
        user id and authorization info
```
This patch log will contain a single patch at this point and EC will _run_ the patch log by 

- first retrieving any resources listed via `H graph <...> ` in the patch and populating a UI tab or window with the contents of each

- then EC performs the _adds_ and _deletes_ in order in the patch to set the UI up with the state when the user saved their work from the previous session.

- when the user starts editing in this state, EC creates a new patch linked back to the previous patch:

```
    H id <patchID_2> .
    H prev <patchID> .
```

- the user proceeds to perform editing operations and when finished either signals that they want to stash the work up to this point, which EC implements via:

```
    PUT http://purl.bdrc.io/sandbox/patchID_2?stash
        header includes the patch log id and user info
        the patch payload
```

EC will include the patch log id information in the `PUT` headers in a form to be defined as the API is fleshed out. `PUT` is used to signal that `patchID_2` is an update to the patchLogID mentioned in the http header.

or the user signals to apply the patch log:

```
    PUT http://purl.bdrc.io/sandbox/patchID_2?apply
        header includes the patch log id and user info
        the patch payload
```
to which ES applies and responds as previously discussed.

### Other user resumes

It may be necessary for another user to resume a previously stashed patch log because the original user needs help or has become unavailable and so on. This will require some means via some combination of groups, roles, permissions and so on that allows another user to resume and review the patch log. This could include the original user designating the next user or group etc. Or in case the original user is unavailable an administrator can reassign. How this case will be managed will require further definition.

### Status and logs

We've already suggested that:

```
    GET http://purl.bdrc.io/sandbox
        user id and authorization info
```

will lead to ES responding with a list of _in progress_ patch logs.

There are several other similar queries that we need to consider.

- request a list of patch logs that the user has in progress, completed, and aborted. A user may want to review their work over a given time period for the purpose of making a status report for example

- request a list of patch logs against a given resource - similar to a git history for a single item in  a git repo

- request a list of patchlogs against a set of resources, possibly defined via a sparql query

This is not necessarily an exhaustive list of requests. It's not clear what endpoint should be used here but it appears that `sandbox` would be overloaded with use cases or more query parameters or ...