package com.cheesecomer.cartmate.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.ui.theme.CartMateTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.lists_title)) }) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding))
    }
}

@Preview
@Composable
private fun ListsScreenPreview() {
    CartMateTheme {
        ListsScreen()
    }
}
