# Reading resources

### Current revision

##### GET `/{qname}/focusgraph`

Returns the focus graph of the resource designated by `qname` (ex: `bdr:P1583`) in a format given by the HTTP `Accept` header (currently only `text/turtle`).

Note that the special keyword `me` can be used in the `qname` variable, in that case it returns the focus graph of the user profile associated with the user.

This does not normally require authentification, except when `qname` is a user profile.

The response contains an `Etag` HTTP header with the revision id of the resource.

##### GET `/{qname}`

Returns the entire graph associated with a resource, as found in the Git repository. Note that `qname` can also be a graph (ex: `bdg:P1583`).

This does not normally require authentification, except when `qname` is a user profile.

The response contains an `Etag` HTTP header with the revision id of the resource.

##### HEAD `/{qname}` and HEAD `/{qname}/focusgraph`

The response only contains an `Etag` HTTP header with the revision id of the resource.

This does not normally require authentification, except when `qname` is a user profile.

### Previous revisions

##### GET `/{qname}/revisions`

Returns a list of revisions for the resource, in json format.

##### GET or HEAD `/{qname}/revision/{revId}` or `/{qname}/revision/{revId}/focusgraph`

Returns the same results as above but for the revision specified by `{revId}`.

# Writing Resources

##### PUT `/{qname}/focusgraph`

Creates a new resource designated by `qname`, with a focus graph as its content. The format of the body of the request must be given in the HTTP `Content-Type` header (currently only `text/turtle`). The content of the request is validated against the shapes and an HTTP `400` code is returned in case the content does not validate.

The response has an empty body, it has an `Etag` header with the new revision. The HTTP status is `201` in case of success and `422` if the resource already exists.

##### POST `/{qname}/focusgraph`

Updates the focus graph of the resource designated by `qname` (which can be `me`, see above). The format of the body of the request must be given in the HTTP `Content-Type` header (currently only `text/turtle`). The content of the request is validated against the shapes and an HTTP `400` code is returned in case the content does not validate.

This always requires authentification. Any user can modify their user profile, but only admins can modify the profile of another user.

This is the preferred method for modifying the data, since it validates everything. When more users are allowed to use the editor, only this endpoint will be open to a broader audience.

The request must contain a `If-Match` HTTP header with the revision id of the resource (as represented in the `Etag` header of the GET endpoints).

The request can optionally contain a `X-Change-Message` HTTP header with a message that will be associated with the revision.

The responsee has an empty body, it has an `Etag` header with the new revision. The HTTP status is:
- `412` if the resource id of the request does not match the current one
- `404` if the resource does not exist at all

##### PUT `/{qname}`

Overwrites the graph for the resource designated by `qname`, in a format given by the HTTP `Accept` header (currently only `text/turtle`). This bypasses all validation.

This always requires admin privileges. This is **not** the preferred method for modifying the data, and should be used for debugging only or for edge cases.

The result of a successful PUT is the same as the result of GET `/{qname}` above, except for the HTTP status that is:
- `201` if the resource has been created
- `200` if the resource has been updated

# Reserving resource IDs

##### GET `/ID/{prefix}`

Gets the highest ID using the prefix `{prefix}`.

##### PUT `/ID/{prefix}?n={n}`

Reserves `{n}` ID(s) starting with `{prefix}` in the system and returns them in a simple text format, one per line. `{n}` is optional and defaults to `1`.

##### PUT `/ID/full/{ID}`

Reserves the full id `{ID}`. If the id was already reserved, returns HTTP code `422` if the resource already exists (but not in the case where the ID has been previous reserved but no resource exists).

# Scan requests (admins only)

##### GET `/scanrequest?IDPrefix={IDPrefix}&onlynonsynced={onlynonsynced}&nbvols={nbvols}&scaninfo={scaninfo}&scaninfo_lang={scaninfo_lang}&instance={instance}&iinstance={iinstance}&ric={ric}&access={access}`

Returns a zip file with the BDRC scan request, and creates or updates an image instance as necessary.

If the optional `{onlynonsynced}` is set to `true` then only non-synced volumes are included in the zip file.

If the optional `{nbvols}` is set to a positive integer, the server will ensure that the image instance has at least `{nbols}` volumes and will create the missing ones if necessary.

If the optional `{scaninfo}` and `{scaninfo_lang}` are set, the server will ensure that the image instance exists and will create it if it doesn't.

`{IDPrefix}` is the ID prefix for the additional volumes that will be created (see above).

`{instance}` is the qname of the instance, only useful in the case an image instance will be created.

`{iinstance}` is the qname of the image instance we are asking a scan request for. Optional for cases where a new scan request needs to be created.

`{access}` the the qname of the access status.

`{ric}` is a boolean representing the restriction in China.

# Withdrawing tool

##### POST `/withdraw?from={from}&to={to}`

Used when two identifiers point to the same entity. One (`{from}`, a qname) will be withdrawn in favor of the other (`{}`). This will not make any modification in `{to}` but will change `{from}` as well as all the documents that have references to it, replacing them with references to `{to}`.

The output of the query is a JSON array containing the local names of the graphs that have been updated in the operation.

# Sync notification (admins only)

##### POST `/notifysync/{wqname}/{iqname}`

Where `{wqname}` is in the form `bdr:W22084` and `{iqname}` in the form `bdr:I0886`. The body of the request should be a json object in the form:

```json
{
	"pages_total": {pagestotal}
}
```

It records the date of the request as the sync date in the database, and updates the total number of pages to `{pagestotal}`, which must be an integer.

##### POST `/notifysync`

with an object in the form:

```json
{
	"{wqname}": {
		"{igqname}": {
			"pages_total": {pagestotal}
		}
	}
}
```

does the same as previous function but allows one call per sync batch instead of one per image group.

# Outlines CSV endpoint

##### GET `/outline/{wqname}`

Returns a csv file with the current state of the outline.

- the endpoint must be used with a digital instance (`W`) and not an instance (`MW`)
- ETag is set to the git revision of the outline
- the endpoint returns a csv file and proposes a file name
- the endpoint doesn't require authentication
- in the rare case where there are multiple outlines, the endpoint returns the most likely one
- the endpoint returns a 401 / 403 if the instance or outline is not in public access (which is rare)
- if no outline exists, the endpoint returns a csv with just the headers


##### PUT `/outline/{wqname}`

Takes a csv file in the body.

The request can optionally contain an `If-Match` HTTP header with the revision id of the resource (as represented in the `Etag` header of the GET endpoints).

The request can optionally contain a `X-Change-Message` HTTP header with a message that will be associated with the revision.

The request can optionally contain a `X-Outline-Attribution` HTTP header with the attribution message that will be associated with the revision.

The responsee has an empty body, it has an `Etag` header with the new revision.