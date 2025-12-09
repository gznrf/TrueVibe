package com.example.truevibe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap.Companion.Square
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gznrf.truevibe.R


var i = 1
@Composable
fun MainScreen() {
    val greetTitle = "Узнай свое настроение"
    val sadTitle = "Вы грустный\nвсе будет \nхорошо!\n"
    val happyTitle = "Вы веселый\nидите нафиг"
    val angryTitle = "Вы злой\nпопробуйте успокоиться!"

    val titlesArray = arrayOf(greetTitle, sadTitle, happyTitle, angryTitle)

    var mainTitleText by remember { mutableStateOf(greetTitle) }

    Column(
        modifier = Modifier.size(1736.dp, 4043.dp)
            .background(Color(0xFF33A6B2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        //Title row
        Row(
            modifier = Modifier.size(322.dp, 72.dp),
              //  .background(Color.Red),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = mainTitleText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(25.dp))

        //Camera row
        Row(
            modifier = Modifier.size(322.dp, 582.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Image(
                painter = painterResource(id = R.drawable.placeholder_face),
                contentDescription = "Фото пользователя",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(582.dp).width(322.dp)
                    .clip(RoundedCornerShape(15.dp))
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        //Buttons row
        Row(
            modifier = Modifier.size(322.dp, 84.dp),
                //.background(Color.Red),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically

        ) {
            IconButton(
                onClick = {
                },
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFF33A6B2))
            ) {
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Кнопка — камера
            IconButton(
                onClick = {
                    mainTitleText = titlesArray[i]
                    if (i == 3){
                        i = -1
                    }
                    i++
                },
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera),
                    contentDescription = "Сделать фото",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Кнопка — обновить
            IconButton(
                onClick = { /* TODO: заменить фото */ },
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh),
                    contentDescription = "Обновить фото",
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }
        }
    }
}
