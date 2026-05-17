package com.example.boardexamreviewer.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * Helper to check if the device has an internet connection.
 */
public class NetworkHelper {

    /**
     * Checks if the phone is connected to WiFi or Mobile Data.
     */
    public static boolean isInternetAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;
        
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;
        
        NetworkCapabilities activeNetwork = connectivityManager.getNetworkCapabilities(network);
        if (activeNetwork == null) return false;
        
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
               activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }
}
