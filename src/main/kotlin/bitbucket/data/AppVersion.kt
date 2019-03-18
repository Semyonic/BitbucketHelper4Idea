package bitbucket.data

import com.fasterxml.jackson.annotation.JsonProperty

data class AppVersion(@JsonProperty("version") val version: String,
                      @JsonProperty("buildNumber") val status: Number,
                      @JsonProperty("buildDate") val buildDate: String,
                      @JsonProperty("displayName") val displayName: String)

