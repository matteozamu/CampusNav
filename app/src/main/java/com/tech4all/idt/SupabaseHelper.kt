package com.tech4all.idt

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class Position(
    // Include 'id' if you want to decode it from the response, mark as nullable
    @SerialName("id") val id: Long? = null,
    @SerialName("label") val label: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double
    // Add other columns like 'created_at' if needed/present in your table
)

@Serializable
data class WifiLocation(
    // Include 'id' if you want to decode it from the response, mark as nullable
    @SerialName("position_id") val position_id: Int? = null,
    @SerialName("ssid") val ssid: String,
    @SerialName("bssid") val bssid: String,
    @SerialName("signal_strength") val signal_strength: Int,
    // Add other columns like 'created_at' if needed/present in your table
)

@Serializable
data class Event(
    @SerialName("id") val id: Long? = null,
    @SerialName("creator_name") val creatorName: String,
    @SerialName("date") val date: String,
    @SerialName("time") val time: String,
    @SerialName("type") val type: String
)

@Serializable
data class MatchedPosition(
    val position_id: Int,
    val name: String
)



object SupabaseHelper {
    // Assume you have your Supabase URL and API key defined as constants or retrieved from configuration
    private const val SUPABASE_URL = "https://vrykwubmpmlwobfjcurg.supabase.co"
    private const val SUPABASE_API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZyeWt3dWJtcG1sd29iZmpjdXJnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDM1MjI1NjksImV4cCI6MjA1OTA5ODU2OX0.4ywLkxN1Br0LqpXz1Tum-LtXdc2D0SukEYTI5J9dnuM"

    // Initialize Supabase client
    private val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_API_KEY
    ) {
        install(Postgrest)
    }

    suspend fun insertPosition(label: String, latitude: Double, longitude: Double): Long? {
        return withContext(Dispatchers.IO) {
            try {
                // Create an instance of your data class
                val newPosition = Position(label = label, latitude = latitude, longitude = longitude)

                // Insert the data class instance directly
                val response = supabase.postgrest["positions"].insert(newPosition) {
                    // Optional: Specify 'returning = Returning.REPRESENTATION' if you want the full inserted row back
                    // Otherwise, the default might be 'MINIMAL' which might not include the 'id' unless specified in RLS policies
                    // or if the primary key is requested implicitly by default in newer versions.
                    // Let's assume default works or add: returning = Returning.REPRESENTATION
                }

                // Decode the response directly into your data class
                val insertedPosition = response.decodeSingle<Position>() // Decode the first (and only) inserted item
                Log.d("Supabase", "Inserted Position: $insertedPosition")
                insertedPosition.id // Return the ID from the decoded object
            } catch (e: Exception) {
                Log.e("Supabase", "Error inserting position: ${e.message}", e) // Log the exception too
                null
            }
        }
    }

    suspend fun insertWifiScan(positionId: Int, ssid: String, bssid: String, signalStrength: Int) {
        withContext(Dispatchers.IO) {
            try {
                val newWifiLocation = WifiLocation(position_id = positionId, ssid = ssid, bssid = bssid, signal_strength = signalStrength)

                // Ensure that the data is serialized correctly
                supabase.postgrest["wifi_location"].insert(newWifiLocation)
                Log.d("Supabase", "Wi-Fi data inserted successfully")
            } catch (e: Exception) {
                Log.e("Supabase", "Error inserting Wi-Fi data: ${e.message}")
            }
        }
    }


    suspend fun getWifiDataForPosition(positionId: Long): List<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = supabase.postgrest["wifi_location"].select {
                    filter {
                        eq("position_id", positionId)
                    }
                }

                response.decodeList<JsonObject>().map { jsonObject ->
                    jsonObject.mapValues { (_, value) ->
                        when {
                            value.jsonPrimitive.isString -> value.jsonPrimitive.content
                            value.jsonPrimitive.longOrNull != null -> value.jsonPrimitive.longOrNull!!
                            value.jsonPrimitive.doubleOrNull != null -> value.jsonPrimitive.doubleOrNull!!
                            value.jsonPrimitive.booleanOrNull != null -> value.jsonPrimitive.booleanOrNull!!
                            else -> value.toString()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Supabase", "Error fetching Wi-Fi data: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun insertEvent(event: Event) {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["events"].insert(event)
                Log.d("Supabase", "Event data inserted successfully")
            } catch (e: Exception) {
                Log.e("Supabase", "Error inserting event data: ${e.localizedMessage}", e)

                // If it's an HttpRequestException, log more context
                if (e is io.github.jan.supabase.exceptions.HttpRequestException) {
                    Log.e("Supabase", "HTTP Request failed: ${e.message}")
                }

                throw e // Optional: rethrow or handle
            }
        }
    }

    suspend fun queryBestMatchingPosition(
        bssids: List<String>,
        signalStrengths: List<Int>
    ): List<Pair<Int, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = supabase.postgrest.rpc(
                    function = "match_position_from_wifi",
                    parameters = mapOf(
                        "bssids" to Json.encodeToJsonElement(bssids),
                        "signals" to Json.encodeToJsonElement(signalStrengths)
                    )
                )
                Log.d("Supabase", "RPC response: $response")


                // Decode the response directly into a list of MatchedPosition
                val matchedPositions = response.decodeList<MatchedPosition>()
                Log.d("Supabase", "Raw JSON: ${response.data}")


                // Map the data class objects to the desired Pair format
                matchedPositions.map { matchedPosition ->
                    Pair(matchedPosition.position_id, matchedPosition.name)
                }

            } catch (e: Exception) {
                Log.e("Supabase", "Error querying matched position: ${e.localizedMessage}", e)
                emptyList()
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getUpcomingEvents(): List<Event> {
        return withContext(Dispatchers.IO) {
            try {
                val currentDate = java.time.LocalDate.now().toString() // formato: "2025-05-07"
                val currentTime = java.time.LocalTime.now().toString().substring(0, 5) // formato: "HH:mm"

                val response = supabase.postgrest["events"].select {
                    filter {
                        or {
                            gt("date", currentDate)
                            and {
                                eq("date", currentDate)
                                gt("time", currentTime)
                            }
                        }
                    }
                    order("date", Order.ASCENDING)
                    order("time", Order.ASCENDING)
                }

                response.decodeList<Event>()
            } catch (e: Exception) {
                Log.e("Supabase", "Error fetching upcoming events: ${e.message}")
                emptyList()
            }
        }
    }




}
