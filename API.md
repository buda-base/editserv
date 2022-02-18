### GET `/{qname}/focusgraph`

Returns the focus graph of the resource designated by `qname` (ex: `bdr:P1583`) in a format given by the HTTP `Accept` header (currently only `text/turtle`).

Note that the special keyword `me` can be used in the `qname` variable, in that case it returns the focus graph of the user profile associated with the user.

This does not normally require authentification, except when `qname` is a user profile.

### GET `/{qname}`

Returns the entire graph associated with a resource, as found in the Git repository. Note that `qname` can also be a graph (ex: `bdg:P1583`).

This does not normally require authentification, except when `qname` is a user profile.

### PUT `/{qname}/focusgraph`

Writes a new focus graph of the resource designated by `qname` (which can be `me`, see above). The format of the body of the request must be given in the HTTP `Content-Type` header (currently only `text/turtle`). The content of the request is validated against the shapes and an HTTP `400` code is returned in case the content does not validate.

This always requires authentification. Any user can modify their user profile, but only admins can modify anything else.

This is the preferred method for modifying the data, since it validates everything. When more users are allowed to use the editor, only this endpoint will be open to a broader audience.

### PUT `/{qname}`

Rewrites the graph for the resource designated by `qname`, in a format given by the HTTP `Accept` header (currently only `text/turtle`). This bypasses all validation.

This always requires admin privileges. This is **not** the preferred method for modifying the data, and should be used for debugging only or for edge cases.


