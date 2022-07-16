package oci.distribution.client.api

import com.haroldadmin.cnradapter.CompletableResponse
import com.haroldadmin.cnradapter.NetworkResponse
import oci.distribution.client.model.exception.ErrorResponse
import oci.distribution.client.model.manifest.ManifestV2
import oci.distribution.client.model.oci.Digest
import oci.distribution.client.model.oci.Reference
import oci.distribution.client.model.oci.Repository
import oci.distribution.client.model.response.Catalog
import oci.distribution.client.model.response.TagList
import okhttp3.ResponseBody
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

    @GET("/v2")
    suspend fun ping(): NetworkResponse<Unit, Unit>

    @GET("/v2/_catalog")
    @Headers("Accept: application/json")
    suspend fun images(@Query("n") limit: Int?, @Query("last") last: Int?): NetworkResponse<Catalog, ErrorResponse>

    // Blobs
    @HEAD("/v2/{name}/blobs/{digest}")
    suspend fun inspectBlob(
        @Path("name") repository: Repository,
        @Path("digest") digest: Digest
    ): NetworkResponse<Response<Void>, Unit>

    @GET("/v2/{name}/blobs/{digest}")
    suspend fun blob(
        @Path("name") repository: Repository,
        @Path("digest") digest: Digest
    ): NetworkResponse<ResponseBody, Unit>

    @DELETE("/v2/{name}/blobs/{digest}")
    suspend fun deleteBlob(
        @Path("name") repository: Repository,
        @Path("digest") digest: Digest
    ): NetworkResponse<Unit, Unit>

    // Manifest
    @GET("/v2/{name}/manifests/{reference}")
    @Headers("Accept: application/vnd.docker.distribution.manifest.v2+json, application/json")
    suspend fun manifest(
        @Path("name") repository: Repository,
        @Path("reference") reference: Reference
    ): NetworkResponse<ManifestV2, ErrorResponse>

    @PUT("/v2/{name}/manifests/{reference}")
    @Headers("Content-Type: application/vnd.docker.distribution.manifest.v2+json, application/json")
    suspend fun createManifest(
        @Path("name") repository: Repository,
        @Path("reference") reference: Reference,
        @Body manifest: ManifestV2
    ): NetworkResponse<Unit, ErrorResponse>

    @DELETE("/v2/{name}/manifests/{reference}")
    @Headers("Accept: application/json")
    suspend fun deleteManifest(
        @Path("name") repository: Repository,
        @Path("reference") digest: Digest
    ): NetworkResponse<Unit, Unit>

    // Tags
    @GET("/v2/{name}/tags/list")
    @Headers("Accept: application/json")
    suspend fun tags(
        @Path("name") repository: Repository,
        @Query("n") limit: Int?,
        @Query("last") last: Int?
    ): NetworkResponse<TagList, ErrorResponse>
}
