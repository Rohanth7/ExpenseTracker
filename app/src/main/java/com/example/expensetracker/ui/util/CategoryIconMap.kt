package com.example.expensetracker.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

fun categoryIcon(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        n.contains("food") || n.contains("dining") || n.contains("restaurant") ||
            n.contains("cafe") || n.contains("swiggy") || n.contains("zomato") ||
            n.contains("eat") -> Icons.Default.Restaurant
        n.contains("transport") || n.contains("commut") || n.contains("cab") ||
            n.contains("uber") || n.contains("ola") || n.contains("rapido") ||
            n.contains("metro") || n.contains("bus") -> Icons.Default.DirectionsCar
        n.contains("shop") || n.contains("cloth") || n.contains("fashion") ||
            n.contains("apparel") || n.contains("myntra") || n.contains("amazon") ||
            n.contains("flipkart") -> Icons.Default.ShoppingBag
        n.contains("entertain") || n.contains("movie") || n.contains("gaming") ||
            n.contains("cinema") || n.contains("pvr") || n.contains("inox") ||
            n.contains("bms") -> Icons.Default.Movie
        n.contains("health") || n.contains("medical") || n.contains("hospital") ||
            n.contains("pharma") || n.contains("doctor") || n.contains("clinic") ||
            n.contains("dental") -> Icons.Default.LocalHospital
        n.contains("electric") || n.contains("utility") || n.contains("internet") ||
            n.contains("wifi") || n.contains("airtel") || n.contains("jio") ||
            n.contains("bsnl") -> Icons.Default.ElectricBolt
        n.contains("rent") || n.contains("hous") || n.contains("home") ||
            n.contains("flat") || n.contains("pg") -> Icons.Default.Home
        n.contains("education") || n.contains("school") || n.contains("college") ||
            n.contains("course") || n.contains("learn") || n.contains("tuition") -> Icons.Default.School
        n.contains("travel") || n.contains("flight") || n.contains("trip") ||
            n.contains("vacation") || n.contains("holiday") || n.contains("tour") -> Icons.Default.FlightTakeoff
        n.contains("grocer") || n.contains("supermarket") || n.contains("mart") ||
            n.contains("kirana") || n.contains("dmart") || n.contains("bigbazaar") -> Icons.Default.LocalGroceryStore
        n.contains("fitness") || n.contains("gym") || n.contains("sport") ||
            n.contains("exercise") || n.contains("yoga") || n.contains("cult") -> Icons.Default.FitnessCenter
        n.contains("invest") || n.contains("bank") || n.contains("saving") ||
            n.contains("finance") || n.contains("stock") || n.contains("mutual") -> Icons.Default.AccountBalance
        n.contains("subscript") || n.contains("stream") || n.contains("netflix") ||
            n.contains("spotify") || n.contains("prime") || n.contains("hotstar") -> Icons.Default.Subscriptions
        n.contains("gift") || n.contains("present") || n.contains("donation") ||
            n.contains("charity") -> Icons.Default.CardGiftcard
        n.contains("fuel") || n.contains("petrol") || n.contains("diesel") ||
            n.contains("gas") || n.contains("cng") -> Icons.Default.LocalGasStation
        n.contains("personal") || n.contains("care") || n.contains("beauty") ||
            n.contains("salon") || n.contains("spa") -> Icons.Default.Face
        n.contains("misc") || n.contains("other") || n.contains("general") -> Icons.Default.MoreHoriz
        else -> Icons.Default.Category
    }
}
