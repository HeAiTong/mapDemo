package com.example.mapdemo

import com.fasterxml.jackson.annotation.JsonProperty

data class RecordDto(
    @JsonProperty("2") val c02: Double? = null,  //lat
    @JsonProperty("3") val c03: Double? = null,  //lon
    @JsonProperty("7") val c07: Double? = null,  //distance
)
