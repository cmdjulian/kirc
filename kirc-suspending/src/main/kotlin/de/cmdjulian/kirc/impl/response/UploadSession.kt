package de.cmdjulian.kirc.impl.response

/**
 * Represents an upload session for uploading a data blob
 *
 * [sessionId] - to identify current session for finishing upload, resuming upload, etc.
 * [location] - the location to store data to. Used to construct the api request url.
 */
// constructed by hand not in serialization / deserialization
class UploadSession internal constructor(val sessionId: String, val location: String)
