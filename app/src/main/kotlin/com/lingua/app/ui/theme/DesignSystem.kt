package com.lingua.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Space { val xs=4.dp; val sm=8.dp; val md=16.dp; val lg=24.dp; val xl=32.dp }
private val Light=lightColorScheme(primary=Color(0xFF326B5C),onPrimary=Color.White,primaryContainer=Color(0xFFD7F0E5),onPrimaryContainer=Color(0xFF163D31),secondary=Color(0xFF576A91),secondaryContainer=Color(0xFFE0E8FC),background=Color(0xFFF6F8F6),surface=Color(0xFFFEFFFC),surfaceVariant=Color(0xFFEAF0EA))
private val Dark=darkColorScheme(primary=Color(0xFFA0D5BF),onPrimary=Color(0xFF12382C),primaryContainer=Color(0xFF244D40),onPrimaryContainer=Color(0xFFD7F0E5),secondary=Color(0xFFB6C6EA),background=Color(0xFF111916),surface=Color(0xFF19231F),surfaceVariant=Color(0xFF29372F))
private val Type=Typography(headlineLarge=androidx.compose.ui.text.TextStyle(fontSize=32.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold),headlineMedium=androidx.compose.ui.text.TextStyle(fontSize=26.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold),titleLarge=androidx.compose.ui.text.TextStyle(fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold),bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=26.sp),labelLarge=androidx.compose.ui.text.TextStyle(fontSize=14.sp,fontWeight=FontWeight.SemiBold))
@Composable fun LinguaTheme(dark: Boolean,content: @Composable ()->Unit) { MaterialTheme(colorScheme=if(dark) Dark else Light,typography=Type,shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),content=content) }
@Composable fun LearningCard(modifier: Modifier=Modifier,highlight: Boolean=false,content: @Composable ColumnScope.()->Unit) {
    Card(modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),shape=MaterialTheme.shapes.large) { Column(Modifier.padding(Space.lg),verticalArrangement=Arrangement.spacedBy(Space.md),content=content) }
}
@Composable fun PrimaryAction(label: String,enabled: Boolean=true,onClick: ()->Unit) { Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=MaterialTheme.shapes.medium) { Text(label) } }
