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

##### GET `/{qname}/scanrequest`

Returns a zip file with the BDRC scan request. It can only work with Image Instance (Scan) qnames (ex: `bdr:W22084`). It responds to a query parameter `?onlynonsynced=true` which makes it select only non-synced volumes. It returns a 404 if there are no volumes to put in the scan request.

# Sync notification (admins only)

##### POST `/{qname}/notifysync?pagestotal={pagestotal}`

This works only for `qname`s of image groups (starting with `bdr:I`). It records the date of the request as the sync date in the database, and updates the total number of pages to `{pagestotal}`, which must be an integer. This operation might be asynchronous and call to the next function is advised at the end of a batch of sync notifications.

##### POST `/notifysynccommit`

Commits the sync notifications so that they are fully integrated into the system.