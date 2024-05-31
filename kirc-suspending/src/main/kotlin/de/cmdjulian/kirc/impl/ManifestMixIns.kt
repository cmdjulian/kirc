package de.cmdjulian.kirc.impl

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import de.cmdjulian.kirc.spec.manifest.DockerManifestListV1
import de.cmdjulian.kirc.spec.manifest.DockerManifestV2
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import de.cmdjulian.kirc.spec.manifest.OciManifestV1

@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "mediaType", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = DockerManifestV2::class, name = DockerManifestV2.MediaType),
    JsonSubTypes.Type(value = DockerManifestListV1::class, name = DockerManifestListV1.MediaType),
    JsonSubTypes.Type(value = OciManifestV1::class, name = OciManifestV1.MediaType),
    JsonSubTypes.Type(value = OciManifestListV1::class, name = OciManifestListV1.MediaType),
)
interface ManifestMixIn

@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "mediaType", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = DockerManifestV2::class, name = DockerManifestV2.MediaType),
    JsonSubTypes.Type(value = OciManifestV1::class, name = OciManifestV1.MediaType),
)
interface ManifestSingleMixIn

@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "mediaType", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = DockerManifestListV1::class, name = DockerManifestListV1.MediaType),
    JsonSubTypes.Type(value = OciManifestListV1::class, name = OciManifestListV1.MediaType),
)
interface ManifestListMixIn
