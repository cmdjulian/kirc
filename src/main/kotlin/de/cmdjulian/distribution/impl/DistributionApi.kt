package de.cmdjulian.distribution.impl

import com.haroldadmin.cnradapter.NetworkResponse
import de.cmdjulian.distribution.impl.response.Catalog
import de.cmdjulian.distribution.impl.response.TagList
import de.cmdjulian.distribution.exception.ErrorResponse
import de.cmdjulian.distribution.spec.manifest.DockerManifestV2
import de.cmdjulian.distribution.model.Digest
import de.cmdjulian.distribution.model.Reference
import de.cmdjulian.distribution.model.Repository
import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

internal interface DistributionApi {

    @GET("/v2")
    suspend fun ping(): NetworkResponse<Unit, Unit>

    @GET("/v2/_catalog")
    @Headers("Accept: application/json")
    suspend fun images(@Query("n") limit: Int?, @Query("last") last: Int?): NetworkResponse<Catalog, ErrorResponse>

    // Blobs
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
    ): NetworkResponse<DockerManifestV2, ErrorResponse>

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
