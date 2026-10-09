package com.coredrive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLConnection

private val Bg = Color(0xFF090B0D)
private val Card = Color(0xFF15191D)
private val Accent = Color(0xFF36D27A)
private const val FREE_BRAZIL_PLAYLIST = "https://iptv-org.github.io/iptv/countries/br.m3u"

data class Channel(val name:String, val url:String, val group:String="Outros", val logo:String?=null, val tvgId:String?=null)
data class Module(val title:String,val icon:String,val route:String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { CoreDriveApp() } }
}

@Composable fun CoreDriveApp(){
    val nav=rememberNavController()
    MaterialTheme(colorScheme=darkColorScheme(background=Bg,surface=Card,primary=Accent)){
        NavHost(navController=nav,startDestination="home",modifier=Modifier.fillMaxSize().background(Bg)){
            composable("home"){Home{nav.navigate(it)}}
            composable("iptv"){IptvScreen{nav.popBackStack()}}
            composable("tv"){TvScreen{nav.popBackStack()}}
            composable("local"){LocalMediaScreen{nav.popBackStack()}}
            composable("favorites"){FavoritesScreen{nav.popBackStack()}}
            composable("music"){SimpleScreen("🎵  Música","Player de áudio e favoritos"){nav.popBackStack()}}
            composable("radio"){SimpleScreen("📻  Rádio","Rádios online e estações favoritas"){nav.popBackStack()}}
            composable("youtube"){SimpleScreen("▶  YouTube","A integração usará mecanismos oficiais e respeitará a conta/assinatura do usuário."){nav.popBackStack()}}
            composable("settings"){SimpleScreen("⚙  Configurações","Licença, aparência, listas IPTV e preferências"){nav.popBackStack()}}
        }
    }
}

@Composable fun Home(onOpen:(String)->Unit){
    val modules=listOf(Module("TV Grátis","📺","tv"),Module("IPTV","📡","iptv"),Module("YouTube","▶","youtube"),Module("Música","🎵","music"),Module("Rádio","📻","radio"),Module("Arquivos","📁","local"),Module("Favoritos","★","favorites"),Module("Configurações","⚙","settings"))
    Column(Modifier.fillMaxSize().padding(24.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("CORE DRIVE",fontSize=30.sp,color=Color.White);Text("Central multimídia",color=Color.Gray)};Text("TV • IPTV • MEDIA",color=Color.LightGray)}
        Spacer(Modifier.height(28.dp))
        LazyVerticalGrid(columns=GridCells.Fixed(4),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.fillMaxSize()){
            items(modules){m->Card(Modifier.fillMaxWidth().height(130.dp).clickable{onOpen(m.route)} ){Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text(m.icon,fontSize=34.sp);Text(m.title,color=Color.White)}}}
        }
    }
}

@Composable fun Header(title:String,onBack:()->Unit){Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){Text("‹",fontSize=38.sp,modifier=Modifier.clickable{onBack()});Spacer(Modifier.width(14.dp));Text(title,fontSize=24.sp,color=Color.White)}}

@Composable fun IptvScreen(onBack:()->Unit){
    var url by remember{mutableStateOf("")}
    var channels by remember{mutableStateOf(emptyList<Channel>())}
    var status by remember{mutableStateOf("Cole uma URL M3U/M3U8 ou carregue a lista pública")}
    var selected by remember{mutableStateOf<Channel?>(null)}
    var search by remember{mutableStateOf("")}
    var group by remember{mutableStateOf("Todos")}
    val context= LocalContext.current
    val favorites= remember { context.getSharedPreferences("core_drive", android.content.Context.MODE_PRIVATE) }
    var favoriteUrls by remember { mutableStateOf(favorites.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    val scope=rememberCoroutineScope()
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null) scope.launch {
            status="Lendo arquivo M3U…"
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()} ?: error("Não foi possível abrir o arquivo") }.let{parseM3uText(it)} }
                .onSuccess { channels=it; selected=null; group="Todos"; status="${it.size} canais importados do arquivo" }
                .onFailure { status="Falha ao importar: ${it.message ?: "arquivo inválido"}" }
        }
    }
    val groups=listOf("Todos") + channels.map{it.group}.distinct().sorted()
    val visible=channels.filter{(group=="Todos"||it.group==group) && it.name.contains(search,true)}
    fun toggleFavorite(c:Channel){ val next=favoriteUrls.toMutableSet(); if(!next.add(c.url)) next.remove(c.url); favoriteUrls=next; favorites.edit().putStringSet("favorites",next).apply() }
    suspend fun load(source:String, label:String){ status="Carregando $label…"; runCatching{parseM3u(source)}.onSuccess{channels=it;selected=null;group="Todos";status="${it.size} canais carregados • streams públicos podem ficar indisponíveis"}.onFailure{status="Falha ao carregar: ${it.message ?: "verifique a URL e a conexão"}"} }
    Column(Modifier.fillMaxSize().background(Bg)){
        Header("📡  IPTV",onBack)
        Row(Modifier.fillMaxSize().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Column(Modifier.width(390.dp)){
                OutlinedTextField(value=url,onValueChange={url=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("URL M3U / M3U8")})
                Spacer(Modifier.height(8.dp))
                Button(onClick={scope.launch{load(url,"lista IPTV")}},modifier=Modifier.fillMaxWidth()){Text("IMPORTAR POR URL")}
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick={filePicker.launch(arrayOf("*/*"))},modifier=Modifier.fillMaxWidth()){Text("ABRIR ARQUIVO M3U") }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick={url=FREE_BRAZIL_PLAYLIST;scope.launch{load(FREE_BRAZIL_PLAYLIST,"TV pública")}},modifier=Modifier.fillMaxWidth()){Text("CARREGAR TV GRÁTIS")}
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Buscar canal")})
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    groups.forEach{g-> FilterChip(selected=group==g,onClick={group=g},label={Text(g,maxLines=1)}) }
                }
                Text(status,color=Color.Gray,fontSize=12.sp,modifier=Modifier.padding(vertical=8.dp))
                LazyColumn(verticalArrangement=Arrangement.spacedBy(3.dp)){
                    lazyItems(visible,key={it.url}){c->
                        Row(Modifier.fillMaxWidth().background(if(selected?.url==c.url) Card else Bg).clickable{selected=c}.padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){Text(c.name,color=Color.White,maxLines=2);Text(c.group,color=Color.Gray,fontSize=11.sp)}
                            Text(if(c.url in favoriteUrls) "★" else "☆",color=Accent,modifier=Modifier.clickable{toggleFavorite(c)}.padding(8.dp))
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()){
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)){
                    selected?.let{Player(channel=it)} ?: Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Selecione um canal para reproduzir",color=Color.Gray)}
                }
                selected?.let{Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(it.name,color=Color.White);Text(it.group,color=Color.Gray,fontSize=12.sp)};TextButton(onClick={toggleFavorite(it)}){Text(if(it.url in favoriteUrls) "★ Favorito" else "☆ Favoritar")}}}
            }
        }
    }
}

@Composable fun TvScreen(onBack:()->Unit){
    var channels by remember{mutableStateOf(emptyList<Channel>())}; var selected by remember{mutableStateOf<Channel?>(null)}; var loading by remember{mutableStateOf(true)}; var error by remember{mutableStateOf("")}; var search by remember{mutableStateOf("")}; var group by remember{mutableStateOf("Todos")}
    val context=LocalContext.current
    val prefs= remember { context.getSharedPreferences("core_drive", android.content.Context.MODE_PRIVATE) }
    var favoriteUrls by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){scope.launch{runCatching{parseM3u(FREE_BRAZIL_PLAYLIST)}.onSuccess{channels=it}.onFailure{error=it.message ?: "Não foi possível carregar a lista"}.also{loading=false}}}
    val groups=listOf("Todos")+channels.map{it.group}.distinct().sorted()
    val visible=channels.filter{(group=="Todos"||it.group==group)&&it.name.contains(search,true)}
    fun toggle(c:Channel){val n=favoriteUrls.toMutableSet();if(!n.add(c.url))n.remove(c.url);favoriteUrls=n;prefs.edit().putStringSet("favorites",n).apply()}
    Column(Modifier.fillMaxSize().background(Bg)){
        Header("📺  TV Grátis",onBack)
        Row(Modifier.fillMaxSize().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Column(Modifier.width(400.dp)){
                OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Buscar canal")})
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){groups.forEach{g->FilterChip(selected=group==g,onClick={group=g},label={Text(g,maxLines=1)})}}
                if(loading) Text("Carregando lista pública…",color=Color.Gray)
                if(error.isNotBlank()) Text("Erro: $error",color=Color(0xFFFF8A80))
                Text("${visible.size} canais na lista • disponibilidade varia por emissora",color=Color.Gray,fontSize=12.sp,modifier=Modifier.padding(vertical=6.dp))
                LazyColumn(verticalArrangement=Arrangement.spacedBy(3.dp)){
                    lazyItems(visible,key={it.url}){c->Row(Modifier.fillMaxWidth().background(if(selected?.url==c.url) Card else Bg).clickable{selected=c}.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(c.name,color=Color.White,maxLines=2);Text(c.group,color=Color.Gray,fontSize=11.sp)};Text(if(c.url in favoriteUrls)"★" else "☆",color=Accent,modifier=Modifier.clickable{toggle(c)}.padding(8.dp))}}
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()){
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)){selected?.let{Player(it)}?:Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Selecione um canal gratuito",color=Color.Gray)}}
                selected?.let{Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(it.name,color=Color.White);Text("Fonte pública agregada; não hospedamos os streams",color=Color.Gray,fontSize=12.sp)};TextButton(onClick={toggle(it)}){Text(if(it.url in favoriteUrls)"★ Favorito" else "☆ Favoritar")}}}
            }
        }
    }
}

@Composable fun Player(channel:Channel){
    val context=LocalContext.current
    val player=remember(channel.url){ExoPlayer.Builder(context).build().apply{setMediaItem(MediaItem.fromUri(channel.url));prepare();playWhenReady=true}}
    DisposableEffect(player){onDispose{player.release()}}
    AndroidView(factory={PlayerView(it).apply{this.player=player;useController=true}},modifier=Modifier.fillMaxSize())
}

suspend fun parseM3u(source:String):List<Channel> = withContext(Dispatchers.IO){
    require(source.startsWith("http://",true)||source.startsWith("https://",true)){"Use uma URL HTTP ou HTTPS válida"}
    val connection: URLConnection = URL(source).openConnection().apply { connectTimeout=12000; readTimeout=20000; setRequestProperty("User-Agent","CoreDrive/0.4") }
    val text=connection.getInputStream().bufferedReader(Charsets.UTF_8).use{it.readText()}
    parseM3uText(text)
}

fun parseM3uText(text:String):List<Channel>{
    require(text.isNotBlank()) { "A lista retornou vazia" }
    val result=mutableListOf<Channel>(); var pendingName=""; var group="Outros"; var logo:String?=null; var tvgId:String?=null
    fun attr(key:String,line:String)=Regex("(?:^|\\s)${Regex.escape(key)}=\\\"([^\\\"]*)\\\"",RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)
    text.lineSequence().forEach{line->val l=line.trim(); when{
        l.startsWith("#EXTINF",true)->{pendingName=l.substringAfterLast(",").trim().ifBlank{"Canal"};group=attr("group-title",l)?.ifBlank{"Outros"}?:"Outros";logo=attr("tvg-logo",l);tvgId=attr("tvg-id",l)}
        l.isNotEmpty()&&!l.startsWith("#")&&pendingName.isNotEmpty()->{if(l.startsWith("http://",true)||l.startsWith("https://",true))result.add(Channel(pendingName,l,group,logo,tvgId));pendingName="";group="Outros";logo=null;tvgId=null}
    }}
    require(result.isNotEmpty()){"Nenhum canal válido foi encontrado no arquivo/lista"}
    result.distinctBy{it.url}
}

@Composable fun LocalMediaScreen(onBack:()->Unit){
    val context=LocalContext.current
    var mediaUri by remember{mutableStateOf<android.net.Uri?>(null)}
    var mediaName by remember{mutableStateOf("Nenhum arquivo selecionado")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){mediaUri=uri;mediaName=uri.lastPathSegment?.substringAfterLast('/') ?: "Arquivo selecionado";runCatching{context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}}
    }
    Column(Modifier.fillMaxSize().background(Bg)){
        Header("📁  Arquivos locais",onBack)
        Row(Modifier.fillMaxSize().padding(20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Column(Modifier.width(300.dp)){
                Text("Reproduza vídeos e músicas armazenados no aparelho ou em provedores de arquivos.",color=Color.Gray)
                Spacer(Modifier.height(16.dp))
                Button(onClick={picker.launch(arrayOf("video/*","audio/*"))},modifier=Modifier.fillMaxWidth()){Text("ESCOLHER MÍDIA")}
                Spacer(Modifier.height(8.dp));Text(mediaName,color=Color.White)
            }
            Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black),contentAlignment=Alignment.Center){
                mediaUri?.let{uri-> LocalMediaPlayer(uri) } ?: Text("Selecione um arquivo para começar",color=Color.Gray)
            }
        }
    }
}

@Composable fun LocalMediaPlayer(uri:android.net.Uri){
    val context=LocalContext.current
    val player=remember(uri){ExoPlayer.Builder(context).build().apply{setMediaItem(MediaItem.fromUri(uri));prepare();playWhenReady=true}}
    DisposableEffect(player){onDispose{player.release()}}
    AndroidView(factory={PlayerView(it).apply{this.player=player;useController=true}},modifier=Modifier.fillMaxSize(),update={it.player=player})
}

@Composable fun FavoritesScreen(onBack:()->Unit){
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("core_drive",android.content.Context.MODE_PRIVATE)}
    var favoriteUrls by remember{mutableStateOf(prefs.getStringSet("favorites",emptySet())?.toSet()?:emptySet())}
    var selected by remember{mutableStateOf<Channel?>(null)}
    Column(Modifier.fillMaxSize().background(Bg)){
        Header("★  Favoritos",onBack)
        if(favoriteUrls.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Seus canais favoritos aparecerão aqui.",color=Color.Gray)}
        else Row(Modifier.fillMaxSize().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){
            LazyColumn(Modifier.width(400.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                lazyItems(favoriteUrls.toList(),key={it}){url->
                    Row(Modifier.fillMaxWidth().clickable{selected=Channel(url.substringAfterLast('/').substringBefore('?').ifBlank{"Canal favorito"},url)}.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Text(url,color=Color.White,modifier=Modifier.weight(1f),maxLines=2);TextButton(onClick={val next=favoriteUrls-url;favoriteUrls=next;prefs.edit().putStringSet("favorites",next).apply();if(selected?.url==url)selected=null}){Text("Remover")}}
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black),contentAlignment=Alignment.Center){selected?.let{Player(it)}?:Text("Selecione um favorito",color=Color.Gray)}
        }
    }
}

@Composable fun SimpleScreen(title:String,desc:String,onBack:()->Unit){Column(Modifier.fillMaxSize().background(Bg)){Header(title,onBack);Column(Modifier.padding(24.dp)){Text(desc,color=Color.Gray,fontSize=18.sp)}}}
