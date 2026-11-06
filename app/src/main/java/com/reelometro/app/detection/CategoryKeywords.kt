package com.reelometro.app.detection

import com.reelometro.app.data.Category

/**
 * Palabras clave por categoría para [CategoryClassifier]. Todo **normalizado**: minúsculas, sin tildes
 * y con la ñ convertida en n («uñas» → «unas», «español» → «espanol»).
 *
 * - [Keywords.words]: palabra exacta (token completo de la descripción o hashtag completo).
 * - [Keywords.tagOnly]: solo cuentan como hashtag completo (#ia, #uñas, #amor): en el texto son
 *   demasiado ambiguas («ia», «unas vacaciones», «vino» de venir, «pasta» de dinero…).
 * - [Keywords.stems]: raíces que también cuentan dentro de palabras y hashtags compuestos
 *   (#recetasfaciles, #gymmotivation, #tiktokfood). Solo raíces de ≥ 4 letras y sin choques conocidos:
 *   «moto» no es raíz (motorola), «histori» tampoco (historias = Stories), «gol» tampoco (golosinas).
 * - [Keywords.phrases]: expresiones de varias palabras («inteligencia artificial»).
 *
 * Si una palabra es exacta en alguna categoría, las raíces de otras no cuentan para ese token
 * (#motorola es Tecnología, no Motor).
 */
internal object CategoryKeywords {

    class Keywords(
        val words: Set<String>,
        val stems: List<String> = emptyList(),
        val tagOnly: Set<String> = emptySet(),
        val phrases: List<String> = emptyList(),
    )

    private fun words(vararg w: String) = w.toSet()

    val BY_CATEGORY: Map<Category, Keywords> = linkedMapOf(
        Category.HUMOR to Keywords(
            words = words(
                "humor", "meme", "memes", "comedia", "comedy", "funny", "risa", "risas", "broma", "bromas", "prank",
                "pranks", "chiste", "chistes", "xd", "lol", "lmao", "sketch", "parodia", "parody", "humorista",
                "comico", "comica", "standup", "monologo", "gracioso", "graciosa", "troll", "trolleo", "cachondeo",
                "humortiktok", "memestiktok",
            ),
            stems = listOf("humor", "meme", "comedia", "comedy", "funny", "broma", "chiste", "prank", "parodia"),
        ),
        Category.DEPORTE to Keywords(
            words = words(
                "futbol", "football", "soccer", "nba", "basket", "baloncesto", "basketball", "gol", "goles",
                "golazo", "golazos", "tennis", "padel", "f1", "formula1", "motogp", "boxeo", "boxing", "ufc", "mma",
                "deporte", "deportes", "sport", "sports", "laliga", "champions", "messi", "ronaldo", "cr7", "mbappe",
                "realmadrid", "barca", "fcbarcelona", "atleti", "ciclismo", "cycling", "golf", "rugby", "voleibol",
                "volleyball", "balonmano", "handball", "nfl", "superbowl", "eurocopa", "premierleague", "futbolista",
                "arbitro", "penalti", "penalty", "hattrick", "atletismo", "natacion", "swimming", "surf", "skate",
                "skateboarding", "esqui", "ski", "snowboard", "escalada", "climbing", "karate", "judo", "taekwondo",
                "jiujitsu", "bjj", "kickboxing", "wrestling", "wwe", "tenis",
            ),
            stems = listOf(
                "futbol", "football", "soccer", "basket", "balonces", "deporte", "champions", "laliga", "golazo",
                "padel", "boxeo", "ciclis", "atletism", "natacion", "escalad",
            ),
            tagOnly = words("var", "mundial", "premier", "seleccion", "portero", "lucha"),
        ),
        Category.FITNESS to Keywords(
            words = words(
                "gym", "gimnasio", "fitness", "workout", "entreno", "entrenar", "entrenamiento", "musculacion",
                "crossfit", "calistenia", "calisthenics", "proteina", "proteinas", "protein", "cardio",
                "bodybuilding", "culturismo", "gymtok", "fittok", "abs", "abdominales", "sentadillas", "squat",
                "squats", "pesas", "hiit", "pilates", "yoga", "running", "runner", "maraton", "marathon",
                "estiramientos", "stretching", "gains", "bulking", "dieta", "macros", "adelgazar", "weightloss",
                "perderpeso",
            ),
            stems = listOf(
                "gym", "fitness", "workout", "entren", "muscula", "calisteni", "crossfit", "bodybuild", "culturism",
                "proteina", "cardio", "pilates", "abdominal", "sentadilla", "maraton", "weightloss",
            ),
            tagOnly = words("fit", "rutina", "correr"),
        ),
        Category.COMIDA to Keywords(
            words = words(
                "receta", "recetas", "recipe", "recipes", "food", "comida", "comidas", "cocina", "cocinar",
                "cooking", "foodie", "chef", "postre", "postres", "dessert", "foodtok", "restaurante", "restaurant",
                "tortilla", "paella", "pizza", "hamburguesa", "burger", "tapas", "sushi", "tacos", "desayuno",
                "breakfast", "almuerzo", "lunch", "cena", "dinner", "merienda", "bizcocho", "tarta", "pastel", "cake",
                "panaderia", "bakery", "airfryer", "freidora", "mukbang", "streetfood", "vegano", "vegana", "vegan",
                "keto", "cafe", "coffee", "cocktail", "coctel", "cerveza", "beer", "golosina", "golosinas",
                "chocolate", "helado", "icecream", "gastronomia", "yummy", "delicious", "delicioso", "deliciosa",
            ),
            stems = listOf(
                "receta", "recipe", "comida", "cocina", "cooking", "foodie", "food", "postre", "pasteler",
                "reposter", "gastronom", "panader", "mukbang", "desayun", "hamburgues", "airfryer", "freidora",
            ),
            tagOnly = words("pan", "pasta", "vino", "wine"),
        ),
        Category.MUSICA to Keywords(
            words = words(
                "musica", "music", "song", "songs", "cancion", "canciones", "rap", "reggaeton", "trap", "concierto",
                "concert", "guitarra", "guitar", "piano", "dj", "singer", "cantante", "cantar", "singing", "lyrics",
                "musico", "flamenco", "rock", "kpop", "jazz", "techno", "edm", "bachata", "cumbia", "corrido",
                "corridos", "bateria", "drums", "violin", "ukelele", "karaoke", "vinilo", "vinyl", "album", "spotify",
                "newmusic", "productor", "producer", "freestyle",
            ),
            stems = listOf(
                "musica", "music", "cancion", "concierto", "guitar", "piano", "cantante", "reggaeton", "flamenco",
                "karaoke",
            ),
            tagOnly = words("cover", "covers", "pop", "metal", "beat", "beats", "letra"),
        ),
        Category.BAILE to Keywords(
            words = words(
                "dance", "dancing", "dancer", "baile", "bailes", "bailar", "bailando", "bailarina", "bailarin",
                "coreografia", "choreo", "choreography", "dancechallenge", "twerk", "breakdance", "shuffle",
                "tiktokdance",
            ),
            stems = listOf("dance", "baila", "baile", "coreograf", "choreo"),
        ),
        Category.GAMING to Keywords(
            words = words(
                "gaming", "gamer", "gamers", "videojuego", "videojuegos", "fortnite", "minecraft", "gta", "gta6",
                "valorant", "leagueoflegends", "twitch", "streamer", "clashroyale", "brawlstars", "eafc", "fifa",
                "roblox", "ps5", "ps4", "playstation", "xbox", "nintendo", "pokemon", "videogames", "videogame",
                "gameplay", "warzone", "callofduty", "overwatch", "zelda", "speedrun", "esports", "freefire",
                "amongus", "genshin", "fnaf", "pcgamer", "pcgaming", "retrogaming",
            ),
            stems = listOf(
                "gaming", "gamer", "videojuego", "fortnite", "minecraft", "valorant", "roblox", "pokemon",
                "playstation", "nintendo", "gameplay", "esports", "speedrun",
            ),
            tagOnly = words("cod", "apex", "juegos"),
        ),
        Category.APRENDIZAJE to Keywords(
            words = words(
                "aprende", "aprender", "aprendizaje", "learn", "learning", "educacion", "education", "educativo",
                "ciencia", "ciencias", "science", "history", "curiosidades", "curiosidad", "sabiasque",
                "datoscuriosos", "datocurioso", "study", "studytok", "idiomas", "idioma", "ingles", "english",
                "matematicas", "math", "maths", "fisica", "physics", "quimica", "chemistry", "biologia", "biology",
                "psicologia", "psychology", "filosofia", "philosophy", "libros", "libro", "books", "book",
                "booktok", "lectura", "reading", "programacion", "programming", "coding", "explicacion",
                "explained", "documental", "documentary", "astronomia", "astronomy", "universo", "universe",
                "geografia", "geography", "culturageneral", "trivia", "oposiciones", "universidad", "university",
                "examen", "selectividad", "ebau", "profesor", "profesora", "profe", "teacher", "estudiar",
            ),
            stems = listOf(
                "aprend", "learn", "educa", "cienci", "scienc", "curiosid", "sabiasque", "datoscurioso",
                "datocurioso", "studytok", "idioma", "matemat", "psicolog", "filosof", "booktok", "programac",
                "programming", "documental", "astronom", "culturageneral", "oposicion",
            ),
            tagOnly = words("historia", "leer", "estudio"),
        ),
        Category.TECNOLOGIA to Keywords(
            words = words(
                "tech", "tecnologia", "technology", "iphone", "android", "samsung", "apple", "xiaomi", "huawei",
                "motorola", "oneplus", "gadget", "gadgets", "unboxing", "techtok", "chatgpt", "openai", "gpt",
                "inteligenciaartificial", "artificialintelligence", "iagenerativa", "smartphone", "movil", "moviles",
                "ordenador", "portatil", "laptop", "computer", "software", "hardware", "robot", "robotica", "robotics",
                "drone", "dron", "ciberseguridad", "cybersecurity", "hacker", "hacking", "linux", "ipad", "airpods",
                "smartwatch", "auriculares", "headphones", "midjourney",
            ),
            stems = listOf(
                "tecnolog", "technolog", "gadget", "unboxing", "smartphone", "iphone", "samsung", "xiaomi", "chatgpt",
                "inteligenciaartificial", "robotic", "cibersegur", "cybersecur",
            ),
            tagOnly = words("ia", "ai", "pc", "setup", "pixel", "tecno"),
            phrases = listOf("inteligencia artificial", "artificial intelligence", "realidad virtual", "virtual reality"),
        ),
        Category.BELLEZA to Keywords(
            words = words(
                "makeup", "maquillaje", "moda", "fashion", "outfit", "outfits", "ootd", "skincare", "beauty",
                "belleza", "peinado", "peinados", "hair", "hairstyle", "nails", "manicura", "manicure", "grwm",
                "getreadywithme", "labial", "lipstick", "perfume", "perfumes", "cosmetica", "cosmetics", "sephora",
                "zara", "shein", "haul", "streetwear", "sneakers", "zapatillas", "vestido", "dress", "ropa", "clothes",
            ),
            stems = listOf(
                "makeup", "maquilla", "skincare", "beauty", "belleza", "fashion", "outfit", "peinado", "hairstyl",
                "manicur", "cosmetic", "perfum", "streetwear", "grwm",
            ),
            tagOnly = words("unas", "style", "estilo", "look", "looks"),
        ),
        Category.ANIMALES to Keywords(
            words = words(
                "perro", "perros", "perrito", "perritos", "dog", "dogs", "doggo", "gato", "gatos", "gatito", "gatitos",
                "cat", "cats", "kitten", "kittens", "puppy", "puppies", "cachorro", "cachorros", "animales", "animal",
                "animals", "pets", "pet", "mascota", "mascotas", "caballo", "caballos", "horse", "conejo", "hamster",
                "loro", "pajaro", "aves", "birds", "bird", "acuario", "reptil", "serpiente", "snake", "tortuga", "zoo",
                "fauna", "wildlife", "veterinario", "dogsoftiktok", "catsoftiktok", "petsoftiktok", "michi", "michis",
                "lomito", "lomitos",
            ),
            stems = listOf("perrit", "perros", "gatit", "gatos", "puppy", "kitten", "mascota", "animal", "dogsof", "catsof", "petsof", "michi"),
        ),
        Category.VIAJES to Keywords(
            words = words(
                "viaje", "viajes", "travel", "viajar", "playa", "beach", "turismo", "tourism", "vacaciones", "vacation",
                "holidays", "paisaje", "paisajes", "naturaleza", "nature", "traveltok", "montana", "mountain",
                "roadtrip", "mochilero", "backpacking", "hotel", "hoteles", "airbnb", "aeropuerto", "airport", "crucero",
                "cruise", "isla", "island", "wanderlust", "senderismo", "hiking", "camping", "acampada", "escapada",
            ),
            stems = listOf(
                "viaj", "travel", "turism", "vacacion", "paisaj", "senderism", "mochiler", "backpack", "wanderlust",
                "roadtrip", "escapada",
            ),
        ),
        Category.MOTOR to Keywords(
            words = words(
                "coche", "coches", "car", "cars", "moto", "motos", "motor", "motores", "supercar", "supercars", "drift",
                "drifting", "tuning", "carro", "carros", "automovil", "automoviles", "ferrari", "lamborghini", "porsche",
                "bmw", "audi", "tesla", "cupra", "toyota", "kawasaki", "ducati", "harley", "motero", "motera", "biker",
                "mecanica", "mecanico", "mechanic", "gasolina", "rally", "karting", "carspotting", "jdm", "carsoftiktok",
                "motorsport",
            ),
            stems = listOf("coche", "supercar", "motero", "automovil", "carspotting", "carsof", "drift", "tuning"),
            tagOnly = words("seat", "mercedes"),
        ),
        Category.DINERO to Keywords(
            words = words(
                "dinero", "money", "finanzas", "finance", "inversion", "inversiones", "invertir", "investing", "crypto",
                "cripto", "criptomonedas", "bitcoin", "btc", "ethereum", "negocio", "negocios", "emprender",
                "emprendimiento", "emprendedor", "emprendedora", "business", "marketing", "ahorro", "ahorrar",
                "ahorros", "sueldo", "salario", "hipoteca", "impuestos", "hacienda", "ingresos", "trading", "trader",
                "forex", "dropshipping", "ecommerce", "startup", "entrepreneur", "millonario", "riqueza", "wealth",
                "bolsadevalores",
            ),
            stems = listOf(
                "dinero", "money", "financ", "invers", "invert", "cripto", "crypto", "bitcoin", "emprend", "negocio",
                "business", "marketing", "ahorr", "trading", "dropship", "ecommerce", "entrepreneur", "millonari",
            ),
        ),
        Category.NOTICIAS to Keywords(
            words = words(
                "noticias", "noticia", "news", "politica", "politico", "politicos", "politics", "gobierno", "elecciones",
                "election", "elections", "economia", "actualidad", "ultimahora", "breakingnews", "congreso", "senado",
                "presidente", "ministro", "ministra", "parlamento", "manifestacion", "protesta", "protest", "huelga",
                "periodismo", "journalism", "telediario", "informativo", "ucrania", "ukraine", "gaza", "palestina",
                "palestine", "putin", "trump", "guerra",
            ),
            stems = listOf("noticia", "news", "politic", "eleccion", "election", "actualidad", "ultimahora", "periodis", "manifestac"),
            tagOnly = words("sanchez", "feijoo", "abascal", "ayuso", "israel", "ley"),
            phrases = listOf("ultima hora", "breaking news"),
        ),
        Category.RELACIONES to Keywords(
            words = words(
                "pareja", "parejas", "novio", "novia", "novios", "relacion", "relaciones", "relationship",
                "relationships", "couple", "couples", "boyfriend", "girlfriend", "dating", "ligar", "ligoteo", "crush",
                "toxico", "toxica", "matrimonio", "boda", "bodas", "wedding", "marido", "esposa", "esposo", "husband",
                "wife", "enamorado", "enamorada", "enamorados", "sanvalentin", "couplegoals", "tinder", "ruptura",
                "breakup", "desamor", "infidelidad", "celos",
            ),
            stems = listOf("pareja", "relacion", "relationship", "couple", "novio", "enamor", "sanvalentin", "matrimoni", "infidel"),
            tagOnly = words("amor", "love", "ex", "citas"),
        ),
    )
}
