package oci.distribution.client

import oci.distribution.client.model.domain.Digest
import oci.distribution.client.model.domain.Reference
import oci.distribution.client.model.domain.Repository
import oci.distribution.client.model.manifest.ManifestV2
import oci.distribution.client.model.response.Catalog
import oci.distribution.client.model.response.TagList
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Headers
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

internal interface DistributionApi {

    @GET("/")
    fun ping(): Response<Unit>

    @GET("/_catalog")
    @Headers("Accept: application/json")
    fun images(@Query("n") limit: Int?, @Query("last") last: Int?): Response<Catalog>

    // Blobs
    @HEAD("/{name}/blobs/{digest}")
    fun inspectBlob(@Path("name") repository: Repository, @Path("digest") digest: Digest): Response<Unit>

    @GET("/{name}/blobs/{digest}")
    fun blob(@Path("name") repository: Repository, @Path("digest") digest: Digest): Response<Array<Byte>>

    @DELETE("/{name}/blobs/{digest}")
    fun deleteBlob(@Path("name") repository: Repository, @Path("digest") digest: Digest): Response<Unit>

    // Manifest
    @HEAD("/{name}/manifests/{reference}")
    @Headers("Accept: application/vnd.docker.distribution.manifest.v2+json")
    fun inspectManifest(@Path("name") repository: Repository, @Path("reference") reference: Reference): Response<Unit>

    @GET("/{name}/manifests/{reference}")
    @Headers("Accept: application/vnd.docker.distribution.manifest.v2+json")
    fun manifest(@Path("name") repository: Repository, @Path("reference") reference: Reference): Response<ManifestV2>

    @PUT("/{name}/manifests/{reference}")
    @Headers("Content-Type: application/vnd.docker.distribution.manifest.v2+json")
    fun createManifest(
        @Path("name") repository: Repository,
        @Path("reference") reference: Reference,
        @Body manifest: ManifestV2
    ): Response<Unit>

    @DELETE("/{name}/manifests/{reference}")
    @Headers("Accept: application/json")
    fun deleteManifest(@Path("name") repository: Repository, @Path("reference") digest: Digest): Response<Unit>

    // Tags
    @GET("/{name}/tags/list")
    @Headers("Accept: application/json")
    fun tags(@Path("name") repository: Repository, @Query("n") limit: Int?, @Query("last") last: Int?):
        Response<TagList>
}
