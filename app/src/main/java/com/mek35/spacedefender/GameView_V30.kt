package com.mek35.spacedefender

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    private enum class State { MENU, HANGAR, STATS, PLAYING, PAUSED, GAME_OVER }
    private enum class EnemyType { SCOUT, TANK, ZIGZAG, GUNNER, DRONE, KAMIKAZE, ELITE, BOSS }
    private enum class PowerType { RAPID, SHIELD, SPREAD, HEAL, OVERDRIVE, MAGNET }
    private enum class Weapon { LASER, SPREAD, PLASMA, PULSE, RAIL }
    private enum class Difficulty { EASY, NORMAL, HARD }

    private data class Bullet(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var damage: Int, val enemy: Boolean = false, var pierce: Int = 0
    )

    private data class Enemy(
        var x: Float, var y: Float, var r: Float, var speed: Float,
        var hp: Int, val maxHp: Int, val type: EnemyType,
        var phase: Float = Random.nextFloat() * 6.28318f,
        var lastShot: Long = 0L,
        val bossStyle: Int = 0
    )

    private data class PowerUp(var x: Float, var y: Float, val type: PowerType, var speed: Float = 150f)
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var size: Float)

    private var state = State.MENU
    private var score = 0
    private var level = 1
    private var health = 100
    private var maxHealth = 100
    private var best = 0
    private var credits = 0
    private var runCredits = 0
    private var combo = 1
    private var kills = 0
    private var bossesKilled = 0
    private var elitesKilled = 0
    private var missionProgress = 0
    private var missionComplete = false
    private var lastKillAt = 0L
    private var shieldUntil = 0L
    private var rapidUntil = 0L
    private var spreadUntil = 0L
    private var overdriveUntil = 0L
    private var magnetUntil = 0L
    private var lastBossLevel = 0
    private var lastRewardLevel = 1
    private var selectedShip = 0
    private var unlockedShips = 1
    private var weaponLevel = 1
    private var armorLevel = 1
    private var difficulty = Difficulty.NORMAL
    private var weapon = Weapon.LASER
    private var soundOn = true
    private var vibrationOn = true

    private var totalKills = 0
    private var totalBosses = 0
    private var totalCreditsEarned = 0
    private var longestCombo = 1
    private var runsPlayed = 0

    private var playerX = 0f
    private var playerY = 0f
    private var targetX = 0f
    private var lastFrame = 0L
    private var lastShot = 0L
    private var lastSpawn = 0L
    private var shooting = false

    private val bullets = mutableListOf<Bullet>()
    private val enemies = mutableListOf<Enemy>()
    private val powerUps = mutableListOf<PowerUp>()
    private val particles = mutableListOf<Particle>()

    private val prefs = context.getSharedPreferences("space_defender_v30", Context.MODE_PRIVATE)
    private val v10Prefs = context.getSharedPreferences("space_defender_v10", Context.MODE_PRIVATE)
    private val oldPrefs = context.getSharedPreferences("space_defender_v2", Context.MODE_PRIVATE)
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val stars = MutableList(180) { Pair(Random.nextFloat(), Random.nextFloat()) }

    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val cyan = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65, 220, 255) }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 80, 100) }
    private val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 220, 80) }
    private val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 245, 150) }
    private val purple = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 100, 255) }
    private val orange = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 150, 70) }
    private val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 125, 255) }
    private val pink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 95, 190) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        best = maxOf(prefs.getInt("best", 0), v10Prefs.getInt("best", 0), oldPrefs.getInt("best", 0))
        credits = if (prefs.contains("credits")) prefs.getInt("credits", 0) else v10Prefs.getInt("credits", 0)
        unlockedShips = if (prefs.contains("unlockedShips")) prefs.getInt("unlockedShips", 1) else v10Prefs.getInt("unlockedShips", 1)
        unlockedShips = unlockedShips.coerceIn(1, 5)
        selectedShip = if (prefs.contains("selectedShip")) prefs.getInt("selectedShip", 0) else v10Prefs.getInt("selectedShip", 0)
        selectedShip = selectedShip.coerceIn(0, unlockedShips - 1)
        weaponLevel = (if (prefs.contains("weaponLevel")) prefs.getInt("weaponLevel", 1) else v10Prefs.getInt("weaponLevel", 1)).coerceIn(1, 10)
        armorLevel = (if (prefs.contains("armorLevel")) prefs.getInt("armorLevel", 1) else v10Prefs.getInt("armorLevel", 1)).coerceIn(1, 10)
        val d = if (prefs.contains("difficulty")) prefs.getInt("difficulty", 1) else v10Prefs.getInt("difficulty", 1)
        difficulty = Difficulty.values()[d.coerceIn(0, Difficulty.values().lastIndex)]
        val w = if (prefs.contains("weapon")) prefs.getInt("weapon", 0) else v10Prefs.getInt("weapon", 0)
        weapon = Weapon.values()[w.coerceIn(0, Weapon.values().lastIndex)]
        soundOn = if (prefs.contains("soundOn")) prefs.getBoolean("soundOn", true) else v10Prefs.getBoolean("soundOn", true)
        vibrationOn = if (prefs.contains("vibrationOn")) prefs.getBoolean("vibrationOn", true) else v10Prefs.getBoolean("vibrationOn", true)
        totalKills = prefs.getInt("totalKills", 0)
        totalBosses = prefs.getInt("totalBosses", 0)
        totalCreditsEarned = prefs.getInt("totalCreditsEarned", 0)
        longestCombo = prefs.getInt("longestCombo", 1)
        runsPlayed = prefs.getInt("runsPlayed", 0)
        saveProgress()
        isFocusable = true
    }

    override fun onDetachedFromWindow() {
        tone.release()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(2, 3, 14))
        drawStars(canvas)
        when (state) {
            State.MENU -> drawMenu(canvas)
            State.HANGAR -> drawHangar(canvas)
            State.STATS -> drawStats(canvas)
            State.PLAYING -> {
                updateGame()
                drawGame(canvas)
                postInvalidateOnAnimation()
            }
            State.PAUSED -> {
                drawGame(canvas)
                drawOverlay(canvas, "PAUSED", "Tap center to resume")
            }
            State.GAME_OVER -> {
                drawGame(canvas)
                drawGameOver(canvas)
            }
        }
    }

    private fun drawGame(canvas: Canvas) {
        drawHud(canvas)
        drawPlayer(canvas)
        drawBullets(canvas)
        drawEnemies(canvas)
        drawPowerUps(canvas)
        drawParticles(canvas)
        if (state == State.PLAYING || state == State.PAUSED) drawPauseButton(canvas)
    }

    private fun drawStars(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        stars.forEachIndexed { index, star ->
            p.alpha = 50 + (index * 31 % 200)
            val size = when { index % 19 == 0 -> 3.6f; index % 7 == 0 -> 2.2f; else -> 1.05f }
            canvas.drawCircle(star.first * width, star.second * height, size, p)
        }
    }

    private fun drawMenu(canvas: Canvas) {
        text.textSize = width * .102f; text.color = cyan.color
        canvas.drawText("SPACE DEFENDER", width / 2f, height * .16f, text)
        text.textSize = width * .058f; text.color = yellow.color
        canvas.drawText("V30", width / 2f, height * .215f, text)
        text.textSize = width * .026f; text.color = Color.LTGRAY
        canvas.drawText("ELITES • 5 SHIPS • 5 WEAPONS • 5 BOSS PATTERNS", width / 2f, height * .265f, text)
        drawButton(canvas, height * .385f, "PLAY", cyan.color)
        drawButton(canvas, height * .495f, "HANGAR / UPGRADES", purple.color)
        drawButton(canvas, height * .605f, "STATS", blue.color)
        drawButton(canvas, height * .715f, "DIFFICULTY: ${difficulty.name}", orange.color)
        text.textSize = width * .032f; text.color = yellow.color
        canvas.drawText("BEST $best    CREDITS $credits", width / 2f, height * .805f, text)
        text.textSize = width * .025f; text.color = Color.LTGRAY
        canvas.drawText("Drag to move • Hold to fire • Boss every 5 levels", width / 2f, height * .858f, text)
        text.textSize = width * .026f
        text.color = if (soundOn) green.color else red.color
        canvas.drawText("SND ${if (soundOn) "ON" else "OFF"}", width * .36f, height * .93f, text)
        text.color = if (vibrationOn) green.color else red.color
        canvas.drawText("VIB ${if (vibrationOn) "ON" else "OFF"}", width * .64f, height * .93f, text)
    }

    private fun drawHangar(canvas: Canvas) {
        text.textSize = width * .072f; text.color = cyan.color
        canvas.drawText("V30 HANGAR", width / 2f, height * .095f, text)
        text.textSize = width * .032f; text.color = yellow.color
        canvas.drawText("CREDITS $credits", width / 2f, height * .145f, text)

        val shipNames = arrayOf("INTERCEPTOR", "PHANTOM", "TITAN", "NOVA", "WRAITH")
        text.textSize = width * .042f; text.color = white.color
        canvas.drawText(shipNames[selectedShip], width / 2f, height * .225f, text)
        drawPreviewShip(canvas, width / 2f, height * .315f, selectedShip)
        text.textSize = width * .026f; text.color = Color.LTGRAY
        val shipBonus = when (selectedShip) {
            1 -> "+15% fire speed"
            2 -> "+35 max HP"
            3 -> "+1 weapon damage"
            4 -> "+20% fire speed, -10 max HP"
            else -> "Balanced starter ship"
        }
        canvas.drawText(shipBonus, width / 2f, height * .395f, text)

        drawSmallButton(canvas, height * .465f, "PREV", .08f, .46f, purple.color)
        drawSmallButton(canvas, height * .465f, "NEXT / UNLOCK", .54f, .92f, purple.color)
        drawButton(canvas, height * .565f, "WEAPON: ${weapon.name}", blue.color)
        drawButton(canvas, height * .665f, "WEAPON Lv$weaponLevel / 10", yellow.color)
        drawButton(canvas, height * .765f, "ARMOR Lv$armorLevel / 10", green.color)
        drawButton(canvas, height * .865f, "BACK", cyan.color)
        text.textSize = width * .023f; text.color = Color.LTGRAY
        canvas.drawText("Unlocks: Phantom 800 • Titan 1800 • Nova 3200 • Wraith 5000", width / 2f, height * .94f, text)
    }

    private fun drawStats(canvas: Canvas) {
        text.textSize = width * .075f; text.color = cyan.color
        canvas.drawText("CAREER STATS", width / 2f, height * .13f, text)
        val lines = listOf(
            "Best Score: $best",
            "Runs Played: $runsPlayed",
            "Total Kills: $totalKills",
            "Bosses Defeated: $totalBosses",
            "Credits Earned: $totalCreditsEarned",
            "Longest Combo: x$longestCombo",
            "Ships Unlocked: $unlockedShips / 5",
            "Weapon Level: $weaponLevel / 10",
            "Armor Level: $armorLevel / 10"
        )
        text.textSize = width * .038f; text.color = white.color
        lines.forEachIndexed { i, line -> canvas.drawText(line, width / 2f, height * (.25f + i * .06f), text) }
        drawButton(canvas, height * .86f, "BACK", cyan.color)
    }

    private fun drawPreviewShip(canvas: Canvas, x: Float, y: Float, ship: Int) {
        val p = when (ship) { 1 -> purple; 2 -> orange; 3 -> green; 4 -> pink; else -> cyan }
        val path = Path()
        when (ship) {
            1 -> { path.moveTo(x, y - 58f); path.lineTo(x - 48f, y + 22f); path.lineTo(x - 12f, y + 10f); path.lineTo(x, y + 32f); path.lineTo(x + 12f, y + 10f); path.lineTo(x + 48f, y + 22f); path.close() }
            2 -> { path.moveTo(x, y - 55f); path.lineTo(x - 54f, y + 34f); path.lineTo(x - 20f, y + 20f); path.lineTo(x, y + 30f); path.lineTo(x + 20f, y + 20f); path.lineTo(x + 54f, y + 34f); path.close() }
            3 -> { path.moveTo(x, y - 62f); path.lineTo(x - 52f, y + 20f); path.lineTo(x - 18f, y + 8f); path.lineTo(x, y + 38f); path.lineTo(x + 18f, y + 8f); path.lineTo(x + 52f, y + 20f); path.close() }
            4 -> { path.moveTo(x, y - 64f); path.lineTo(x - 35f, y + 34f); path.lineTo(x, y + 12f); path.lineTo(x + 35f, y + 34f); path.close(); canvas.drawCircle(x - 33f, y + 20f, 8f, p); canvas.drawCircle(x + 33f, y + 20f, 8f, p) }
            else -> { path.moveTo(x, y - 58f); path.lineTo(x - 42f, y + 32f); path.lineTo(x, y + 12f); path.lineTo(x + 42f, y + 32f); path.close() }
        }
        canvas.drawPath(path, p)
        canvas.drawCircle(x, y - 8f, 10f, white)
    }

    private fun drawButton(canvas: Canvas, y: Float, label: String, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; this.color = color }
        canvas.drawRoundRect(width * .16f, y - 38f, width * .84f, y + 38f, 22f, 22f, p)
        text.textSize = width * .04f; text.color = white.color
        canvas.drawText(label, width / 2f, y + 14f, text)
    }

    private fun drawSmallButton(canvas: Canvas, y: Float, label: String, left: Float, right: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; this.color = color }
        canvas.drawRoundRect(width * left, y - 34f, width * right, y + 34f, 18f, 18f, p)
        text.textSize = width * .027f; text.color = white.color
        canvas.drawText(label, width * ((left + right) / 2f), y + 10f, text)
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        val shade = Paint().apply { color = 0xC8000000.toInt() }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
        text.textSize = width * .09f; text.color = cyan.color
        canvas.drawText(title, width / 2f, height * .39f, text)
        text.textSize = width * .038f; text.color = white.color
        canvas.drawText(subtitle, width / 2f, height * .51f, text)
        canvas.drawText("Tap top-left for menu", width / 2f, height * .58f, text)
    }

    private fun drawGameOver(canvas: Canvas) {
        val shade = Paint().apply { color = 0xD0000000.toInt() }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
        text.textSize = width * .078f; text.color = red.color
        canvas.drawText("MISSION ENDED", width / 2f, height * .29f, text)
        text.textSize = width * .041f; text.color = white.color
        canvas.drawText("Score $score   Level $level", width / 2f, height * .37f, text)
        canvas.drawText("Kills $kills   Bosses $bossesKilled   Elites $elitesKilled", width / 2f, height * .43f, text)
        text.color = yellow.color
        canvas.drawText("+$runCredits credits", width / 2f, height * .49f, text)
        text.textSize = width * .031f; text.color = if (missionComplete) green.color else Color.LTGRAY
        canvas.drawText(if (missionComplete) "CONTRACT COMPLETE +150" else missionText(), width / 2f, height * .56f, text)
        drawButton(canvas, height * .69f, "PLAY AGAIN", cyan.color)
        drawButton(canvas, height * .80f, "MAIN MENU", purple.color)
    }

    private fun drawHud(canvas: Canvas) {
        text.textAlign = Paint.Align.LEFT; text.textSize = width * .03f; text.color = white.color
        canvas.drawText("SCORE $score", 14f, 34f, text)
        canvas.drawText("LV $level", 14f, 67f, text)
        text.color = yellow.color
        canvas.drawText("¢ $runCredits", 14f, 100f, text)

        val barLeft = width * .27f; val barTop = 14f; val barRight = width * .72f; val barBottom = 38f
        val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 45, 60) }
        canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, 12f, 12f, back)
        val fraction = health.coerceIn(0, maxHealth).toFloat() / maxHealth.toFloat()
        val hpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = when { fraction > .60f -> green.color; fraction > .30f -> yellow.color; else -> red.color } }
        canvas.drawRoundRect(barLeft, barTop, barLeft + (barRight - barLeft) * fraction, barBottom, 12f, 12f, hpPaint)
        text.textAlign = Paint.Align.CENTER; text.textSize = width * .022f; text.color = white.color
        canvas.drawText("HP $health/$maxHealth", width / 2f, 34f, text)
        text.textSize = width * .024f; text.color = cyan.color
        canvas.drawText("${weapon.name} Lv$weaponLevel", width / 2f, 66f, text)
        if (combo > 1) {
            text.textAlign = Paint.Align.RIGHT; text.textSize = width * .028f; text.color = yellow.color
            canvas.drawText("x$combo", width - 18f, 67f, text)
        }
        text.textAlign = Paint.Align.CENTER; text.textSize = width * .021f; text.color = Color.LTGRAY
        canvas.drawText(missionText(), width / 2f, 98f, text)

        val now = System.currentTimeMillis()
        val active = mutableListOf<String>()
        if (now < shieldUntil) active += "SHIELD"
        if (now < rapidUntil) active += "RAPID"
        if (now < spreadUntil) active += "SPREAD"
        if (now < overdriveUntil) active += "OVERDRIVE"
        if (now < magnetUntil) active += "MAGNET"
        if (active.isNotEmpty()) {
            text.textSize = width * .02f; text.color = purple.color
            canvas.drawText(active.joinToString(" • "), width / 2f, 124f, text)
        }
    }

    private fun drawPauseButton(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; style = Paint.Style.FILL }
        canvas.drawRoundRect(width - 96f, 14f, width - 18f, 88f, 18f, 18f, p)
        val bars = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white.color; strokeWidth = 8f }
        canvas.drawLine(width - 70f, 34f, width - 70f, 68f, bars)
        canvas.drawLine(width - 44f, 34f, width - 44f, 68f, bars)
    }

    private fun drawPlayer(canvas: Canvas) {
        if (playerX == 0f) { playerX = width / 2f; playerY = height * .84f; targetX = playerX }
        val p = when (selectedShip) { 1 -> purple; 2 -> orange; 3 -> green; 4 -> pink; else -> cyan }
        drawPreviewShip(canvas, playerX, playerY, selectedShip)
        canvas.drawCircle(playerX - 12f, playerY + 34f, 6f, yellow)
        canvas.drawCircle(playerX + 12f, playerY + 34f, 6f, yellow)
        if (System.currentTimeMillis() < shieldUntil) {
            val shield = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.color; style = Paint.Style.STROKE; strokeWidth = 5f; alpha = 180 }
            canvas.drawCircle(playerX, playerY, 62f, shield)
        }
    }

    private fun drawBullets(canvas: Canvas) {
        bullets.forEach {
            val p = if (it.enemy) red else when (weapon) {
                Weapon.LASER -> yellow; Weapon.SPREAD -> cyan; Weapon.PLASMA -> purple; Weapon.PULSE -> green; Weapon.RAIL -> pink
            }
            if (it.enemy) canvas.drawCircle(it.x, it.y, 7f, p)
            else if (weapon == Weapon.PULSE) canvas.drawCircle(it.x, it.y, 9f, p)
            else canvas.drawRoundRect(it.x - 5f, it.y - 14f, it.x + 5f, it.y + 14f, 5f, 5f, p)
        }
    }

    private fun drawEnemies(canvas: Canvas) {
        enemies.forEach { enemy ->
            when (enemy.type) {
                EnemyType.SCOUT -> { canvas.drawCircle(enemy.x, enemy.y, enemy.r, red); canvas.drawCircle(enemy.x, enemy.y, enemy.r * .33f, white) }
                EnemyType.TANK -> {
                    val p = Path(); p.moveTo(enemy.x, enemy.y - enemy.r); p.lineTo(enemy.x - enemy.r, enemy.y)
                    p.lineTo(enemy.x - enemy.r * .65f, enemy.y + enemy.r); p.lineTo(enemy.x + enemy.r * .65f, enemy.y + enemy.r); p.lineTo(enemy.x + enemy.r, enemy.y); p.close(); canvas.drawPath(p, orange)
                }
                EnemyType.ZIGZAG -> {
                    val p = Path(); p.moveTo(enemy.x, enemy.y - enemy.r); p.lineTo(enemy.x - enemy.r, enemy.y + enemy.r); p.lineTo(enemy.x, enemy.y + enemy.r * .4f); p.lineTo(enemy.x + enemy.r, enemy.y + enemy.r); p.close(); canvas.drawPath(p, purple)
                }
                EnemyType.GUNNER -> { canvas.drawRoundRect(enemy.x - enemy.r, enemy.y - enemy.r * .7f, enemy.x + enemy.r, enemy.y + enemy.r * .7f, 12f, 12f, blue); canvas.drawCircle(enemy.x, enemy.y + enemy.r * .75f, enemy.r * .24f, red) }
                EnemyType.DRONE -> { canvas.drawCircle(enemy.x, enemy.y, enemy.r, green); canvas.drawLine(enemy.x - enemy.r * 1.4f, enemy.y, enemy.x + enemy.r * 1.4f, enemy.y, white) }
                EnemyType.KAMIKAZE -> {
                    val p = Path(); p.moveTo(enemy.x, enemy.y + enemy.r); p.lineTo(enemy.x - enemy.r, enemy.y - enemy.r); p.lineTo(enemy.x, enemy.y - enemy.r * .4f); p.lineTo(enemy.x + enemy.r, enemy.y - enemy.r); p.close(); canvas.drawPath(p, pink)
                }
                EnemyType.ELITE -> { canvas.drawCircle(enemy.x, enemy.y, enemy.r, yellow); canvas.drawCircle(enemy.x, enemy.y, enemy.r * .68f, red); canvas.drawCircle(enemy.x, enemy.y, enemy.r * .28f, white) }
                EnemyType.BOSS -> drawBoss(canvas, enemy)
            }
        }
    }

    private fun drawBoss(canvas: Canvas, enemy: Enemy) {
        val bossPaint = when (enemy.bossStyle % 5) { 1 -> purple; 2 -> orange; 3 -> blue; 4 -> pink; else -> red }
        if (enemy.bossStyle % 5 == 1 || enemy.bossStyle % 5 == 4) {
            val p = Path(); p.moveTo(enemy.x, enemy.y - enemy.r); p.lineTo(enemy.x - enemy.r * 1.45f, enemy.y); p.lineTo(enemy.x, enemy.y + enemy.r); p.lineTo(enemy.x + enemy.r * 1.45f, enemy.y); p.close(); canvas.drawPath(p, bossPaint)
        } else {
            canvas.drawRoundRect(enemy.x - enemy.r * 1.4f, enemy.y - enemy.r * .7f, enemy.x + enemy.r * 1.4f, enemy.y + enemy.r * .7f, 24f, 24f, bossPaint)
        }
        canvas.drawCircle(enemy.x, enemy.y, enemy.r * .43f, yellow)
        val hpWidth = enemy.r * 2.5f
        val hpFraction = enemy.hp.coerceAtLeast(0).toFloat() / enemy.maxHp.toFloat()
        canvas.drawRect(enemy.x - hpWidth / 2f, enemy.y - enemy.r - 26f, enemy.x + hpWidth / 2f, enemy.y - enemy.r - 14f, Paint().apply { color = Color.DKGRAY })
        canvas.drawRect(enemy.x - hpWidth / 2f, enemy.y - enemy.r - 26f, enemy.x - hpWidth / 2f + hpWidth * hpFraction, enemy.y - enemy.r - 14f, green)
    }

    private fun drawPowerUps(canvas: Canvas) {
        powerUps.forEach { power ->
            val p = when (power.type) {
                PowerType.RAPID -> yellow; PowerType.SHIELD -> cyan; PowerType.SPREAD -> purple
                PowerType.HEAL -> green; PowerType.OVERDRIVE -> orange; PowerType.MAGNET -> pink
            }
            canvas.drawCircle(power.x, power.y, 21f, p)
            text.textSize = 20f; text.color = Color.BLACK
            val label = when (power.type) { PowerType.RAPID -> "R"; PowerType.SHIELD -> "S"; PowerType.SPREAD -> "3"; PowerType.HEAL -> "+"; PowerType.OVERDRIVE -> "X"; PowerType.MAGNET -> "M" }
            canvas.drawText(label, power.x, power.y + 7f, text)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { particle ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (particle.life > .55f) yellow.color else if (particle.life > .25f) orange.color else red.color
                alpha = (255f * particle.life.coerceIn(0f, 1f)).toInt()
            }
            canvas.drawCircle(particle.x, particle.y, particle.size, p)
        }
    }

    private fun updateGame() {
        val now = System.currentTimeMillis()
        if (lastFrame == 0L) lastFrame = now
        val delta = ((now - lastFrame).coerceAtMost(40L)) / 1000f
        lastFrame = now
        level = 1 + score / 350
        if (level > lastRewardLevel) {
            val gained = (level - lastRewardLevel) * 5
            runCredits += gained
            health = (health + (level - lastRewardLevel) * 3).coerceAtMost(maxHealth)
            lastRewardLevel = level
        }
        playerX += (targetX - playerX) * .27f
        playerX = playerX.coerceIn(50f, width - 50f)

        var fireDelay = when (weapon) { Weapon.LASER -> 175L; Weapon.SPREAD -> 235L; Weapon.PLASMA -> 300L; Weapon.PULSE -> 205L; Weapon.RAIL -> 360L }
        fireDelay -= (weaponLevel - 1) * 12L
        if (selectedShip == 1) fireDelay = (fireDelay * .85f).toLong()
        if (selectedShip == 4) fireDelay = (fireDelay * .80f).toLong()
        if (now < rapidUntil) fireDelay = (fireDelay * .55f).toLong()
        if (now < overdriveUntil) fireDelay = (fireDelay * .62f).toLong()
        fireDelay = fireDelay.coerceAtLeast(55L)
        if (shooting && now - lastShot > fireDelay) { fireVolley(now); lastShot = now }

        bullets.forEach { it.x += it.vx * delta; it.y += it.vy * delta }
        bullets.removeAll { it.y < -80f || it.y > height + 80f || it.x < -80f || it.x > width + 80f }

        if (level % 5 == 0 && level != lastBossLevel && enemies.none { it.type == EnemyType.BOSS }) {
            spawnBoss(); lastBossLevel = level
        }
        val bossAlive = enemies.any { it.type == EnemyType.BOSS }
        val diffSpawn = when (difficulty) { Difficulty.EASY -> 120L; Difficulty.NORMAL -> 0L; Difficulty.HARD -> -120L }
        val spawnRate = (800L - level * 30L + diffSpawn).coerceAtLeast(170L)
        if (!bossAlive && now - lastSpawn > spawnRate) { spawnEnemy(); lastSpawn = now }

        enemies.forEach { enemy ->
            when (enemy.type) {
                EnemyType.ZIGZAG -> { enemy.phase += delta * 4.4f; enemy.x += sin(enemy.phase) * 180f * delta; enemy.y += enemy.speed * delta; enemy.x = enemy.x.coerceIn(enemy.r, width - enemy.r) }
                EnemyType.GUNNER -> { enemy.y += enemy.speed * delta; if (now - enemy.lastShot > (1450L - level * 22L).coerceAtLeast(620L)) { fireEnemy(enemy, false); enemy.lastShot = now } }
                EnemyType.DRONE -> { enemy.phase += delta * 3f; enemy.x += sin(enemy.phase) * 120f * delta; enemy.y += enemy.speed * delta; if (now - enemy.lastShot > 1700L) { fireEnemy(enemy, true); enemy.lastShot = now } }
                EnemyType.KAMIKAZE -> {
                    val dx = playerX - enemy.x; val dy = playerY - enemy.y; val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    enemy.x += dx / len * enemy.speed * delta; enemy.y += dy / len * enemy.speed * delta
                }
                EnemyType.ELITE -> { enemy.phase += delta * 2.2f; enemy.x += sin(enemy.phase) * 95f * delta; enemy.y += enemy.speed * delta; if (now - enemy.lastShot > 1150L) { fireEnemy(enemy, true); fireEnemyOffset(enemy, -120f); fireEnemyOffset(enemy, 120f); enemy.lastShot = now } }
                EnemyType.BOSS -> updateBoss(enemy, now, delta)
                else -> enemy.y += enemy.speed * delta
            }
        }

        powerUps.forEach {
            if (now < magnetUntil) {
                val dx = playerX - it.x; val dy = playerY - it.y; val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                it.x += dx / len * 330f * delta; it.y += dy / len * 330f * delta
            } else it.y += it.speed * delta
        }
        powerUps.removeAll { it.y > height + 50f }
        particles.forEach { it.x += it.vx * delta; it.y += it.vy * delta; it.life -= delta * 1.75f; it.size *= .985f }
        particles.removeAll { it.life <= 0f }

        handleBulletHits()
        handleEnemyBulletHits(now)
        handlePlayerHits(now)
        handlePowerUps(now)
    }

    private fun updateBoss(enemy: Enemy, now: Long, delta: Float) {
        val style = enemy.bossStyle % 5
        enemy.phase += delta * (1.15f + style * .1f)
        enemy.x = width / 2f + sin(enemy.phase) * width * (if (style == 3) .36f else .29f)
        enemy.y = height * .17f + cos(enemy.phase * .7f) * 20f
        val delay = when (style) { 1 -> 900L; 2 -> 650L; 3 -> 760L; 4 -> 520L; else -> 1100L }
        if (now - enemy.lastShot > delay) {
            when (style) {
                1 -> { fireEnemy(enemy, true); fireEnemyOffset(enemy, -170f); fireEnemyOffset(enemy, 170f) }
                2 -> repeat(5) { i -> fireEnemyOffset(enemy, (i - 2) * 110f) }
                3 -> { fireEnemyOffset(enemy, -280f); fireEnemyOffset(enemy, -90f); fireEnemyOffset(enemy, 90f); fireEnemyOffset(enemy, 280f) }
                4 -> { fireEnemy(enemy, true); repeat(3) { i -> fireEnemyOffset(enemy, (i - 1) * 210f) } }
                else -> fireEnemy(enemy, true)
            }
            enemy.lastShot = now
        }
    }

    private fun fireVolley(now: Long) {
        var damageBase = weaponLevel + if (now < overdriveUntil) 2 else 0
        if (selectedShip == 3) damageBase += 1
        when (weapon) {
            Weapon.LASER -> {
                bullets.add(Bullet(playerX, playerY - 56f, 0f, -900f, damageBase))
                if (weaponLevel >= 4) bullets.add(Bullet(playerX + 18f, playerY - 48f, 0f, -900f, damageBase))
                if (weaponLevel >= 8) bullets.add(Bullet(playerX - 18f, playerY - 48f, 0f, -900f, damageBase))
            }
            Weapon.SPREAD -> {
                bullets.add(Bullet(playerX, playerY - 54f, 0f, -830f, damageBase))
                bullets.add(Bullet(playerX, playerY - 48f, -230f, -790f, damageBase))
                bullets.add(Bullet(playerX, playerY - 48f, 230f, -790f, damageBase))
                if (weaponLevel >= 3 || now < spreadUntil) { bullets.add(Bullet(playerX, playerY - 42f, -380f, -720f, damageBase)); bullets.add(Bullet(playerX, playerY - 42f, 380f, -720f, damageBase)) }
                if (weaponLevel >= 8) { bullets.add(Bullet(playerX, playerY - 40f, -500f, -650f, damageBase)); bullets.add(Bullet(playerX, playerY - 40f, 500f, -650f, damageBase)) }
            }
            Weapon.PLASMA -> {
                val damage = damageBase * 2 + 1
                bullets.add(Bullet(playerX, playerY - 58f, 0f, -700f, damage))
                if (weaponLevel >= 4 || now < spreadUntil) { bullets.add(Bullet(playerX - 14f, playerY - 46f, -120f, -680f, damage)); bullets.add(Bullet(playerX + 14f, playerY - 46f, 120f, -680f, damage)) }
            }
            Weapon.PULSE -> {
                val damage = damageBase + 2
                bullets.add(Bullet(playerX - 14f, playerY - 50f, -70f, -820f, damage))
                bullets.add(Bullet(playerX + 14f, playerY - 50f, 70f, -820f, damage))
                if (weaponLevel >= 6) bullets.add(Bullet(playerX, playerY - 58f, 0f, -900f, damage + 1))
            }
            Weapon.RAIL -> {
                val damage = damageBase * 3 + 3
                bullets.add(Bullet(playerX, playerY - 60f, 0f, -1150f, damage, pierce = if (weaponLevel >= 5) 2 else 1))
            }
        }
        playTone(ToneGenerator.TONE_PROP_BEEP, 25)
    }

    private fun fireEnemy(enemy: Enemy, aimed: Boolean) {
        var vx = 0f; var vy = 360f + level * 7f
        if (aimed) {
            val dx = playerX - enemy.x; val dy = playerY - enemy.y
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val speed = 420f + level * 5f
            vx = dx / len * speed; vy = dy / len * speed
        }
        bullets.add(Bullet(enemy.x, enemy.y + enemy.r * .7f, vx, vy, 10, true))
    }

    private fun fireEnemyOffset(enemy: Enemy, vx: Float) {
        bullets.add(Bullet(enemy.x, enemy.y + enemy.r * .7f, vx, 390f + level * 5f, 10, true))
    }

    private fun spawnEnemy() {
        val roll = Random.nextInt(100)
        val type = when {
            level >= 10 && roll < 7 -> EnemyType.ELITE
            level >= 8 && roll < 18 -> EnemyType.KAMIKAZE
            level >= 6 && roll < 30 -> EnemyType.DRONE
            level >= 4 && roll < 43 -> EnemyType.GUNNER
            level >= 3 && roll < 60 -> EnemyType.ZIGZAG
            level >= 2 && roll < 77 -> EnemyType.TANK
            else -> EnemyType.SCOUT
        }
        val hpBoost = when (difficulty) { Difficulty.EASY -> 0; Difficulty.NORMAL -> level / 8; Difficulty.HARD -> 1 + level / 6 }
        val radius: Float; val speed: Float; val hp: Int
        when (type) {
            EnemyType.SCOUT -> { radius = Random.nextInt(20, 28).toFloat(); speed = 160f + level * 8f; hp = 1 + hpBoost }
            EnemyType.TANK -> { radius = Random.nextInt(30, 39).toFloat(); speed = 95f + level * 6f; hp = 4 + level / 4 + hpBoost }
            EnemyType.ZIGZAG -> { radius = Random.nextInt(22, 31).toFloat(); speed = 130f + level * 7f; hp = 2 + level / 6 + hpBoost }
            EnemyType.GUNNER -> { radius = Random.nextInt(25, 34).toFloat(); speed = 105f + level * 5f; hp = 3 + level / 5 + hpBoost }
            EnemyType.DRONE -> { radius = Random.nextInt(21, 29).toFloat(); speed = 125f + level * 6f; hp = 3 + level / 7 + hpBoost }
            EnemyType.KAMIKAZE -> { radius = Random.nextInt(19, 27).toFloat(); speed = 220f + level * 7f; hp = 2 + level / 8 + hpBoost }
            EnemyType.ELITE -> { radius = Random.nextInt(31, 40).toFloat(); speed = 100f + level * 4f; hp = 9 + level / 3 + hpBoost }
            EnemyType.BOSS -> return
        }
        enemies.add(Enemy(Random.nextFloat() * (width - radius * 2f) + radius, -radius, radius, speed, hp, hp, type))
    }

    private fun spawnBoss() {
        val multiplier = when (difficulty) { Difficulty.EASY -> .85f; Difficulty.NORMAL -> 1f; Difficulty.HARD -> 1.28f }
        val hp = ((50 + level * 8) * multiplier).toInt()
        enemies.clear(); bullets.removeAll { it.enemy }
        enemies.add(Enemy(width / 2f, height * .17f, width * .15f, 0f, hp, hp, EnemyType.BOSS, bossStyle = (level / 5 - 1) % 5))
        playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 280)
        vibrate(90)
    }

    private fun handleBulletHits() {
        val playerBullets = bullets.filter { !it.enemy }
        val deadBullets = mutableSetOf<Bullet>()
        val deadEnemies = mutableSetOf<Enemy>()
        for (bullet in playerBullets) {
            for (enemy in enemies) {
                val hitRadius = if (enemy.type == EnemyType.BOSS) enemy.r * 1.38f else enemy.r + 9f
                if (distance(bullet.x, bullet.y, enemy.x, enemy.y) < hitRadius) {
                    enemy.hp -= bullet.damage
                    if (bullet.pierce > 0) bullet.pierce-- else deadBullets.add(bullet)
                    if (enemy.hp <= 0 && enemy !in deadEnemies) { deadEnemies.add(enemy); onEnemyKilled(enemy) }
                    if (bullet in deadBullets) break
                }
            }
        }
        bullets.removeAll(deadBullets)
        enemies.removeAll(deadEnemies)
    }

    private fun onEnemyKilled(enemy: Enemy) {
        val nowKill = System.currentTimeMillis()
        combo = if (nowKill - lastKillAt < 1400L) (combo + 1).coerceAtMost(12) else 1
        longestCombo = maxOf(longestCombo, combo)
        lastKillAt = nowKill
        kills++
        totalKills++
        val basePoints = when (enemy.type) {
            EnemyType.SCOUT -> 12; EnemyType.TANK -> 28; EnemyType.ZIGZAG -> 22; EnemyType.GUNNER -> 32
            EnemyType.DRONE -> 36; EnemyType.KAMIKAZE -> 42; EnemyType.ELITE -> 100; EnemyType.BOSS -> 700
        }
        score += basePoints * combo
        val earned = when (enemy.type) { EnemyType.BOSS -> 60; EnemyType.ELITE -> 12; EnemyType.TANK, EnemyType.GUNNER, EnemyType.DRONE -> 3; EnemyType.KAMIKAZE -> 4; else -> 1 }
        runCredits += earned
        totalCreditsEarned += earned
        missionProgress++
        if (enemy.type == EnemyType.ELITE) elitesKilled++
        makeExplosion(enemy.x, enemy.y, when (enemy.type) { EnemyType.BOSS -> 65; EnemyType.ELITE -> 28; else -> 17 })
        if (enemy.type == EnemyType.BOSS) {
            bossesKilled++; totalBosses++
            health = (health + 40).coerceAtMost(maxHealth)
            spawnGuaranteedPowerUp(enemy.x, enemy.y)
            runCredits += 20; totalCreditsEarned += 20
            playTone(ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE, 350); vibrate(120)
        } else {
            maybeDropPowerUp(enemy.x, enemy.y)
            playTone(ToneGenerator.TONE_PROP_ACK, 42)
        }
        if (!missionComplete && missionProgress >= missionTarget()) {
            missionComplete = true
            runCredits += 150; totalCreditsEarned += 150
            playTone(ToneGenerator.TONE_PROP_PROMPT, 180)
        }
    }

    private fun handleEnemyBulletHits(now: Long) {
        val hits = bullets.filter { it.enemy && distance(it.x, it.y, playerX, playerY) < 38f }
        if (hits.isEmpty()) return
        bullets.removeAll(hits.toSet())
        if (now < shieldUntil) return
        val raw = hits.size * when (difficulty) { Difficulty.EASY -> 7; Difficulty.NORMAL -> 10; Difficulty.HARD -> 13 }
        health -= raw
        combo = 1
        makeExplosion(playerX, playerY, 9); playTone(ToneGenerator.TONE_PROP_NACK, 70); vibrate(45)
        if (health <= 0) endGame()
    }

    private fun handlePlayerHits(now: Long) {
        val hits = enemies.filter { enemy -> enemy.type != EnemyType.BOSS && (enemy.y > height + enemy.r || distance(enemy.x, enemy.y, playerX, playerY) < enemy.r + 34f) }
        if (hits.isEmpty()) return
        enemies.removeAll(hits.toSet())
        if (now < shieldUntil) { hits.forEach { makeExplosion(it.x, it.y, 8) }; return }
        val diffDamage = when (difficulty) { Difficulty.EASY -> .75f; Difficulty.NORMAL -> 1f; Difficulty.HARD -> 1.2f }
        val damage = hits.fold(0) { total, enemy -> total + when (enemy.type) {
            EnemyType.TANK -> 25; EnemyType.ZIGZAG -> 20; EnemyType.GUNNER -> 22; EnemyType.DRONE -> 18; EnemyType.KAMIKAZE -> 32; EnemyType.ELITE -> 35; else -> 15
        } }
        health -= (damage * diffDamage).toInt()
        combo = 1
        makeExplosion(playerX, playerY, 13); playTone(ToneGenerator.TONE_PROP_NACK, 90); vibrate(65)
        if (health <= 0) endGame()
    }

    private fun handlePowerUps(now: Long) {
        val collected = powerUps.filter { distance(it.x, it.y, playerX, playerY) < 52f }
        if (collected.isEmpty()) return
        for (power in collected) when (power.type) {
            PowerType.RAPID -> rapidUntil = maxOf(rapidUntil, now + 9000L)
            PowerType.SHIELD -> shieldUntil = maxOf(shieldUntil, now + 10000L)
            PowerType.SPREAD -> spreadUntil = maxOf(spreadUntil, now + 10000L)
            PowerType.HEAL -> health = (health + 35).coerceAtMost(maxHealth)
            PowerType.OVERDRIVE -> overdriveUntil = maxOf(overdriveUntil, now + 8500L)
            PowerType.MAGNET -> magnetUntil = maxOf(magnetUntil, now + 11000L)
        }
        powerUps.removeAll(collected.toSet()); playTone(ToneGenerator.TONE_PROP_PROMPT, 110); vibrate(30)
    }

    private fun maybeDropPowerUp(x: Float, y: Float) {
        if (Random.nextInt(100) >= 22) return
        val values = PowerType.values()
        powerUps.add(PowerUp(x, y, values[Random.nextInt(values.size)]))
    }

    private fun spawnGuaranteedPowerUp(x: Float, y: Float) {
        val values = PowerType.values()
        powerUps.add(PowerUp(x, y, values[Random.nextInt(values.size)], 115f))
    }

    private fun makeExplosion(x: Float, y: Float, count: Int) {
        repeat(count) {
            val angle = Random.nextFloat() * 6.28318f
            val speed = 70f + Random.nextFloat() * 290f
            particles.add(Particle(x, y, cos(angle) * speed, sin(angle) * speed, .55f + Random.nextFloat() * .45f, 3f + Random.nextFloat() * 7f))
        }
    }

    private fun missionTarget(): Int = 25 + (best / 1200).coerceAtMost(35)
    private fun missionText(): String = "CONTRACT: ${missionProgress.coerceAtMost(missionTarget())}/${missionTarget()} targets"

    private fun endGame() {
        health = 0; shooting = false; state = State.GAME_OVER
        credits += runCredits
        runsPlayed++
        if (score > best) best = score
        saveProgress()
    }

    private fun saveProgress() {
        prefs.edit()
            .putInt("best", best)
            .putInt("credits", credits)
            .putInt("unlockedShips", unlockedShips)
            .putInt("selectedShip", selectedShip)
            .putInt("weaponLevel", weaponLevel)
            .putInt("armorLevel", armorLevel)
            .putInt("difficulty", difficulty.ordinal)
            .putInt("weapon", weapon.ordinal)
            .putBoolean("soundOn", soundOn)
            .putBoolean("vibrationOn", vibrationOn)
            .putInt("totalKills", totalKills)
            .putInt("totalBosses", totalBosses)
            .putInt("totalCreditsEarned", totalCreditsEarned)
            .putInt("longestCombo", longestCombo)
            .putInt("runsPlayed", runsPlayed)
            .apply()
    }

    private fun upgradeWeapon() {
        if (weaponLevel >= 10) return
        val cost = weaponLevel * 220
        if (credits >= cost) { credits -= cost; weaponLevel++; saveProgress(); playTone(ToneGenerator.TONE_PROP_PROMPT, 120) }
        else playTone(ToneGenerator.TONE_PROP_NACK, 90)
    }

    private fun upgradeArmor() {
        if (armorLevel >= 10) return
        val cost = armorLevel * 190
        if (credits >= cost) { credits -= cost; armorLevel++; saveProgress(); playTone(ToneGenerator.TONE_PROP_PROMPT, 120) }
        else playTone(ToneGenerator.TONE_PROP_NACK, 90)
    }

    private fun nextShip() {
        if (selectedShip + 1 < unlockedShips) selectedShip++
        else if (unlockedShips < 5) {
            val costs = intArrayOf(800, 1800, 3200, 5000)
            val cost = costs[unlockedShips - 1]
            if (credits >= cost) { credits -= cost; unlockedShips++; selectedShip = unlockedShips - 1; playTone(ToneGenerator.TONE_PROP_PROMPT, 140) }
            else playTone(ToneGenerator.TONE_PROP_NACK, 90)
        } else selectedShip = 0
        saveProgress()
    }

    private fun previousShip() {
        selectedShip = if (selectedShip > 0) selectedShip - 1 else unlockedShips - 1
        saveProgress()
    }

    private fun cycleWeapon() {
        weapon = Weapon.values()[(weapon.ordinal + 1) % Weapon.values().size]
        saveProgress()
    }

    private fun cycleDifficulty() {
        difficulty = Difficulty.values()[(difficulty.ordinal + 1) % Difficulty.values().size]
        saveProgress()
    }

    private fun playTone(toneType: Int, duration: Int) {
        if (soundOn) tone.startTone(toneType, duration)
    }

    private fun vibrate(ms: Long) {
        if (!vibrationOn) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(ms)
            }
        } catch (_: Exception) { }
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (state) {
                    State.MENU -> handleMenuTouch(event.x, event.y)
                    State.HANGAR -> handleHangarTouch(event.x, event.y)
                    State.STATS -> if (event.y > height * .80f) state = State.MENU
                    State.PLAYING -> {
                        if (event.x > width - 115f && event.y < 110f) { state = State.PAUSED; shooting = false }
                        else { targetX = event.x; shooting = true }
                    }
                    State.PAUSED -> {
                        if (event.x < width * .25f && event.y < height * .25f) state = State.MENU
                        else { state = State.PLAYING; lastFrame = System.currentTimeMillis() }
                    }
                    State.GAME_OVER -> {
                        if (event.y in (height * .63f)..(height * .75f)) startGame()
                        else if (event.y in (height * .75f)..(height * .87f)) state = State.MENU
                    }
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> { if (state == State.PLAYING) { targetX = event.x; shooting = true }; return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { shooting = false; return true }
        }
        return true
    }

    private fun handleMenuTouch(x: Float, y: Float) {
        when {
            y in (height * .34f)..(height * .44f) -> startGame()
            y in (height * .44f)..(height * .55f) -> state = State.HANGAR
            y in (height * .55f)..(height * .66f) -> state = State.STATS
            y in (height * .66f)..(height * .77f) -> cycleDifficulty()
            y > height * .88f && x < width * .50f -> { soundOn = !soundOn; saveProgress() }
            y > height * .88f -> { vibrationOn = !vibrationOn; saveProgress() }
        }
    }

    private fun handleHangarTouch(x: Float, y: Float) {
        when {
            y in (height * .425f)..(height * .505f) && x < width * .50f -> previousShip()
            y in (height * .425f)..(height * .505f) -> nextShip()
            y in (height * .515f)..(height * .615f) -> cycleWeapon()
            y in (height * .615f)..(height * .715f) -> upgradeWeapon()
            y in (height * .715f)..(height * .815f) -> upgradeArmor()
            y > height * .82f -> state = State.MENU
        }
    }

    private fun startGame() {
        score = 0; level = 1; combo = 1; kills = 0; bossesKilled = 0; elitesKilled = 0; runCredits = 0; missionProgress = 0; missionComplete = false
        maxHealth = 100 + (armorLevel - 1) * 14 + if (selectedShip == 2) 35 else if (selectedShip == 4) -10 else 0
        health = maxHealth
        lastKillAt = 0L; shieldUntil = 0L; rapidUntil = 0L; spreadUntil = 0L; overdriveUntil = 0L; magnetUntil = 0L; lastBossLevel = 0; lastRewardLevel = 1
        bullets.clear(); enemies.clear(); powerUps.clear(); particles.clear()
        playerX = width / 2f; playerY = height * .84f; targetX = playerX
        lastFrame = System.currentTimeMillis(); lastShot = 0L; lastSpawn = 0L; shooting = false
        state = State.PLAYING
    }
}
