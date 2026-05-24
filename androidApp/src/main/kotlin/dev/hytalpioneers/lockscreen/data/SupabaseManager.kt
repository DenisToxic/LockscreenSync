package dev.hytalpioneers.lockscreen.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseManager {


    val client = createSupabaseClient(
        supabaseUrl = "",
        supabaseKey = ""
    ) {
        install(Postgrest)
        install(Realtime)
    }
}
