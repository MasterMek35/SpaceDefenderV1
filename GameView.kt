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

    private enum class State { MENU, HANGAR, PLAYING, PAUSED, GAME_OVER }
    private enum class EnemyType { SCOUT, TANK, ZIGZAG, GUNNER, BOSS }
    private enum class PowerType { RAPID, SHIELD, SPREAD, HEAL, OVERDRIVE }
    private enum class Weapon { LASER, SPREAD, PLASMA }
    private enum class Difficulty { EASY, NORMAL, HARD }

    private data class Bullet(var x: Float, var y: Float, var vx: Float, var vy: Float, var damage: Int, val enemy: Boolean = false)
    private data class Enemy(
        var x: Float, var y: Float, var r: Float, var speed: Float,
        var hp: Int, val maxHp: Int, val type: EnemyType,
        var phase: Float = Random.nextFloat() * 6.28f,
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
    private var missionProgress = 0
    private var missionComplete = false
    private var lastKillAt = 0L
    private var shieldUntil = 0L
    private var rapidUntil = 0L
    private var spreadUntil = 0L
    private var overdriveUntil = 0L
    private var lastBossLevel = 0
    private var selectedShip = 0
    private var unlockedShips = 1
    private var weaponLevel = 1
    private var armorLevel = 1
    private var difficulty = Difficulty.NORMAL
    private var weapon = Weapon.LASER
    private var soundOn = true
    private var vibrationOn = true

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

    private val prefs = context.getSharedPreferences("space_defender_v10", Context.MODE_PRIVATE)
    private val oldPrefs = context.getSharedPreferences("space_defender_v2", Context.MODE_PRIVATE)
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val stars = MutableList(140) { Pair(Random.nextFloat(), Random.nextFloat()) }

    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val cyan = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65, 220, 255) }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 80, 100) }
    private val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 220, 80) }
    private val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 245, 150) }
    private val purple = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 100, 255) }
    private val orange = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 150, 70) }
    private val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 125, 255) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        best = maxOf(prefs.getInt("best", 0), oldPrefs.getInt("best", 0))
        credits = prefs.getInt("credits", 0)
        unlockedShips = prefs.getInt("unlockedShips", 1).coerceIn(1, 3)
        selectedShip = prefs.getInt("selectedShip", 0).coerceIn(0, unlockedShips - 1)
        weaponLevel = prefs.getInt("weaponLevel", 1).coerceIn(1, 5)
        armorLevel = prefs.getInt("armorLevel", 1).coerceIn(1, 5)
        difficulty = Difficulty.values()[prefs.getInt("difficulty", 1).coerceIn(0, 2)]
        weapon = Weapon.values()[prefs.getInt("weapon", 0).coerceIn(0, 2)]
        soundOn = prefs.getBoolean("soundOn", true)
        vibrationOn = prefs.getBoolean("vibrationOn", true)
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
            p.alpha = 55 + (index * 23 % 190)
            val size = when { index % 17 == 0 -> 3.5f; index % 6 == 0 -> 2.2f; else -> 1.1f }
            canvas.drawCircle(star.first * width, star.second * height, size, p)
        }
    }

    private fun drawMenu(canvas: Canvas) {
        text.textSize = width * .105f; text.color = cyan.color
        canvas.drawText("SPACE DEFENDER", width / 2f, height * .18f, text)
        text.textSize = width * .055f; text.color = yellow.color
        canvas.drawText("V10", width / 2f, height * .235f, text)
        text.textSize = width * .032f; text.color = Color.LTGRAY
        canvas.drawText("PROGRESSION • SHIPS • WEAPONS • MISSIONS", width / 2f, height * .285f, text)
        drawButton(canvas, height * .43f, "PLAY", cyan.color)
        drawButton(canvas, height * .55f, "HANGAR / UPGRADES", purple.color)
        drawButton(canvas, height * .67f, "DIFFICULTY: ${difficulty.name}", orange.color)
        text.textSize = width * .033f; text.color = yellow.color
        canvas.drawText("BEST $best    CREDITS $credits", width / 2f, height * .79f, text)
        text.textSize = width * .028f; text.color = Color.LTGRAY
        canvas.drawText("Drag to move • Hold to fire • Boss every 5 levels", width / 2f, height * .85f, text)
        canvas.drawText("Tap SND / VIB below to toggle", width / 2f, height * .895f, text)
        text.textSize = width * .027f
        text.color = if (soundOn) green.color else red.color
        canvas.drawText("SND ${if (soundOn) "ON" else "OFF"}", width * .36f, height * .95f, text)
        text.color = if (vibrationOn) green.color else red.color
        canvas.drawText("VIB ${if (vibrationOn) "ON" else "OFF"}", width * .64f, height * .95f, text)
    }

    private fun drawHangar(canvas: Canvas) {
        text.textSize = width * .078f; text.color = cyan.color
        canvas.drawText("HANGAR", width / 2f, height * .12f, text)
        text.textSize = width * .035f; text.color = yellow.color
        canvas.drawText("CREDITS $credits", width / 2f, height * .175f, text)

        val shipNames = arrayOf("INTERCEPTOR", "PHANTOM", "TITAN")
        text.textSize = width * .045f; text.color = white.color
        canvas.drawText(shipNames[selectedShip], width / 2f, height * .26f, text)
        drawPreviewShip(canvas, width / 2f, height * .35f, selectedShip)
        text.textSize = width * .028f; text.color = Color.LTGRAY
        val shipBonus = when (selectedShip) { 1 -> "+15% fire speed"; 2 -> "+35 max HP"; else -> "Balanced starter ship" }
        canvas.drawText(shipBonus, width / 2f, height * .43f, text)

        drawSmallButton(canvas, height * .50f, "PREV SHIP", .08f, .46f, purple.color)
        drawSmallButton(canvas, height * .50f, "NEXT / UNLOCK", .54f, .92f, purple.color)
        drawButton(canvas, height * .61f, "WEAPON: ${weapon.name}", blue.color)
        drawButton(canvas, height * .72f, "UPGRADE WEAPON Lv$weaponLevel", yellow.color)
        drawButton(canvas, height * .83f, "UPGRADE ARMOR Lv$armorLevel", green.color)
        drawButton(canvas, height * .94f, "BACK", cyan.color)
    }

    private fun drawPreviewShip(canvas: Canvas, x: Float, y: Float, ship: Int) {
        val p = when (ship) { 1 -> purple; 2 -> orange; else -> cyan }
        val path = Path()
        if (ship == 2) {
            path.moveTo(x, y - 55f); path.lineTo(x - 54f, y + 34f); path.lineTo(x - 20f, y + 20f)
            path.lineTo(x, y + 30f); path.lineTo(x + 20f, y + 20f); path.lineTo(x + 54f, y + 34f); path.close()
        } else if (ship == 1) {
            path.moveTo(x, y - 58f); path.lineTo(x - 48f, y + 22f); path.lineTo(x - 12f, y + 10f)
            path.lineTo(x, y + 32f); path.lineTo(x + 12f, y + 10f); path.lineTo(x + 48f, y + 22f); path.close()
        } else {
            path.moveTo(x, y - 58f); path.lineTo(x - 42f, y + 32f); path.lineTo(x, y + 12f); path.lineTo(x + 42f, y + 32f); path.close()
        }
        canvas.drawPath(path, p)
        canvas.drawCircle(x, y - 8f, 10f, white)
    }

    private fun drawButton(canvas: Canvas, y: Float, label: String, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; this.color = color }
        canvas.drawRoundRect(width * .16f, y - 42f, width * .84f, y + 42f, 24f, 24f, p)
        text.textSize = width * .043f; text.color = white.color
        canvas.drawText(label, width / 2f, y + 15f, text)
    }

    private fun drawSmallButton(canvas: Canvas, y: Float, label: String, left: Float, right: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; this.color = color }
        canvas.drawRoundRect(width * left, y - 36f, width * right, y + 36f, 18f, 18f, p)
        text.textSize = width * .028f; text.color = white.color
        canvas.drawText(label, width * ((left + right) / 2f), y + 10f, text)
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        val shade = Paint().apply { color = 0xC8000000.toInt() }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
        text.textSize = width * .09f; text.color = cyan.color
        canvas.drawText(title, width / 2f, height * .39f, text)
        text.textSize = width * .042f; text.color = white.color
        canvas.drawText(subtitle, width / 2f, height * .51f, text)
        canvas.drawText("Tap top-left for menu", width / 2f, height * .58f, text)
    }

    private fun drawGameOver(canvas: Canvas) {
        val shade = Paint().apply { color = 0xD0000000.toInt() }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
        text.textSize = width * .085f; text.color = red.color
        canvas.drawText("MISSION ENDED", width / 2f, height * .31f, text)
        text.textSize = width * .045f; text.color = white.color
        canvas.drawText("Score $score   Level $level", width / 2f, height * .39f, text)
        canvas.drawText("Kills $kills   Bosses $bossesKilled", width / 2f, height * .45f, text)
        text.color = yellow.color
        canvas.drawText("+$runCredits credits", width / 2f, height * .51f, text)
        text.textSize = width * .034f; text.color = if (missionComplete) green.color else Color.LTGRAY
        canvas.drawText(if (missionComplete) "MISSION COMPLETE +100" else missionText(), width / 2f, height * .58f, text)
        drawButton(canvas, height * .70f, "PLAY AGAIN", cyan.color)
        drawButton(canvas, height * .82f, "MAIN MENU", purple.color)
    }

    private fun drawHud(canvas: Canvas) {
        text.textAlign = Paint.Align.LEFT; text.textSize = width * .032f; text.color = white.color
        canvas.drawText("SCORE $score", 16f, 35f, text)
        canvas.drawText("LV $level", 16f, 70f, text)
        text.color = yellow.color
        canvas.drawText("¢ $runCredits", 16f, 105f, text)

        val barLeft = width * .28f; val barTop = 16f; val barRight = width * .72f; val barBottom = 40f
        val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 45, 60) }
        canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, 12f, 12f, back)
        val fraction = health.coerceIn(0, maxHealth).toFloat() / maxHealth.toFloat()
        val hpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = when { fraction > .60f -> green.color; fraction > .30f -> yellow.color; else -> red.color } }
        canvas.drawRoundRect(barLeft, barTop, barLeft + (barRight - barLeft) * fraction, barBottom, 12f, 12f, hpPaint)
        text.textAlign = Paint.Align.CENTER; text.textSize = width * .024f; text.color = white.color
        canvas.drawText("HP $health/$maxHealth", width / 2f, 36f, text)
        text.textSize = width * .026f; text.color = cyan.color
        canvas.drawText("${weapon.name} Lv$weaponLevel", width / 2f, 70f, text)
        if (combo > 1) {
            text.textAlign = Paint.Align.RIGHT; text.textSize = width * .029f; text.color = yellow.color
            canvas.drawText("x$combo COMBO", width - 18f, 70f, text)
        }
        text.textAlign = Paint.Align.CENTER; text.textSize = width * .023f; text.color = Color.LTGRAY
        canvas.drawText(missionText(), width / 2f, 102f, text)

        val now = System.currentTimeMillis()
        val active = mutableListOf<String>()
        if (now < shieldUntil) active += "SHIELD"
        if (now < rapidUntil) active += "RAPID"
        if (now < spreadUntil) active += "SPREAD"
        if (now < overdriveUntil) active += "OVERDRIVE"
        if (active.isNotEmpty()) {
            text.textSize = width * .022f; text.color = purple.color
            canvas.drawText(active.joinToString(" • "), width / 2f, 129f, text)
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
        val p = when (selectedShip) { 1 -> purple; 2 -> orange; else -> cyan }
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
            val p = if (it.enemy) red else when (weapon) { Weapon.LASER -> yellow; Weapon.SPREAD -> cyan; Weapon.PLASMA -> purple }
            if (it.enemy) canvas.drawCircle(it.x, it.y, 7f, p)
            else canvas.drawRoundRect(it.x - 5f, it.y - 14f, it.x + 5f, it.y + 14f, 5f, 5f, p)
        }
    }

    private fun drawEnemies(canvas: Canvas) {
        enemies.forEach { enemy ->
            when (enemy.type) {
                EnemyType.SCOUT -> {
                    canvas.drawCircle(enemy.x, enemy.y, enemy.r, red)
                    canvas.drawCircle(enemy.x, enemy.y, enemy.r * .33f, white)
                }
                EnemyType.TANK -> {
                    val p = Path(); p.moveTo(enemy.x, enemy.y - enemy.r); p.lineTo(enemy.x - enemy.r, enemy.y)
                    p.lineTo(enemy.x - enemy.r * .65f, enemy.y + enemy.r); p.lineTo(enemy.x + enemy.r * .65f, enemy.y + enemy.r)
                    p.lineTo(enemy.x + enemy.r, enemy.y); p.close(); canvas.drawPath(p, orange)
                }
                EnemyType.ZIGZAG -> {
                    val p = Path(); p.moveTo(enemy.x, enemy.y - enemy.r); p.lineTo(enemy.x - enemy.r, enemy.y + enemy.r)
                    p.lineTo(enemy.x, enemy.y + enemy.r * .4f); p.lineTo(enemy.x + enemy.r, enemy.y + enemy.r); p.close(); canvas.drawPath(p, purple)
                }
                EnemyType.GUNNER -> {
                    canvas.drawRoundRect(enemy.x - enemy.r, enemy.y - enemy.r * .7f, enemy.x + enemy.r, enemy.y + enemy.r * .7f, 12f, 12f, blue)
                    canvas.drawCircle(enemy.x, enemy.y + enemy.r * .75f, enemy.r * .24f, red)
                }
                EnemyType.BOSS -> drawBoss(canvas, enemy)
            }
        }
    }

    private fun drawBoss(canvas: Canvas, enemy: Enemy) {
        val bossPaint = when (enemy.bossStyle % 3) { 1 -> purple; 2 -> orange; else -> red }
        if (enemy.bossStyle % 3 == 1) {
            val p = Path(); p.moveTo(enemy.x, enemy.y - enemy.r); p.lineTo(enemy.x - enemy.r * 1.45f, enemy.y)
            p.lineTo(enemy.x, enemy.y + enemy.r); p.lineTo(enemy.x + enemy.r * 1.45f, enemy.y); p.close(); canvas.drawPath(p, bossPaint)
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
                PowerType.HEAL -> green; PowerType.OVERDRIVE -> orange
            }
            canvas.drawCircle(power.x, power.y, 21f, p)
            text.textSize = 20f; text.color = Color.BLACK
            val label = when (power.type) { PowerType.RAPID -> "R"; PowerType.SHIELD -> "S"; PowerType.SPREAD -> "3"; PowerType.HEAL -> "+"; PowerType.OVERDRIVE -> "X" }
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
        level = 1 + score / 300
        playerX += (targetX - playerX) * .27f
        playerX = playerX.coerceIn(50f, width - 50f)

        var fireDelay = when (weapon) { Weapon.LASER -> 175L; Weapon.SPREAD -> 235L; Weapon.PLASMA -> 300L }
        fireDelay -= (weaponLevel - 1) * 18L
        if (selectedShip == 1) fireDelay = (fireDelay * .85f).toLong()
        if (now < rapidUntil) fireDelay = (fireDelay * .55f).toLong()
        if (now < overdriveUntil) fireDelay = (fireDelay * .62f).toLong()
        fireDelay = fireDelay.coerceAtLeast(60L)
        if (shooting && now - lastShot > fireDelay) { fireVolley(now); lastShot = now }

        bullets.forEach { it.x += it.vx * delta; it.y += it.vy * delta }
        bullets.removeAll { it.y < -60f || it.y > height + 60f || it.x < -60f || it.x > width + 60f }

        if (level % 5 == 0 && level != lastBossLevel && enemies.none { it.type == EnemyType.BOSS }) {
            spawnBoss(); lastBossLevel = level
        }
        val bossAlive = enemies.any { it.type == EnemyType.BOSS }
        val diffSpawn = when (difficulty) { Difficulty.EASY -> 110L; Difficulty.NORMAL -> 0L; Difficulty.HARD -> -120L }
        val spawnRate = (790L - level * 38L + diffSpawn).coerceAtLeast(190L)
        if (!bossAlive && now - lastSpawn > spawnRate) { spawnEnemy(); lastSpawn = now }

        enemies.forEach { enemy ->
            when (enemy.type) {
                EnemyType.ZIGZAG -> {
                    enemy.phase += delta * 4.4f; enemy.x += sin(enemy.phase) * 180f * delta
                    enemy.y += enemy.speed * delta; enemy.x = enemy.x.coerceIn(enemy.r, width - enemy.r)
                }
                EnemyType.GUNNER -> {
                    enemy.y += enemy.speed * delta
                    if (now - enemy.lastShot > (1450L - level * 25L).coerceAtLeast(650L)) { fireEnemy(enemy, false); enemy.lastShot = now }
                }
                EnemyType.BOSS -> updateBoss(enemy, now, delta)
                else -> enemy.y += enemy.speed * delta
            }
        }

        powerUps.forEach { it.y += it.speed * delta }
        powerUps.removeAll { it.y > height + 50f }
        particles.forEach { it.x += it.vx * delta; it.y += it.vy * delta; it.life -= delta * 1.75f; it.size *= .985f }
        particles.removeAll { it.life <= 0f }

        handleBulletHits()
        handleEnemyBulletHits(now)
        handlePlayerHits(now)
        handlePowerUps(now)
    }

    private fun updateBoss(enemy: Enemy, now: Long, delta: Float) {
        enemy.phase += delta * (1.25f + enemy.bossStyle * .12f)
        enemy.x = width / 2f + sin(enemy.phase) * width * .29f
        enemy.y = height * .17f + cos(enemy.phase * .7f) * 20f
        val delay = when (enemy.bossStyle % 3) { 1 -> 900L; 2 -> 650L; else -> 1100L }
        if (now - enemy.lastShot > delay) {
            when (enemy.bossStyle % 3) {
                1 -> { fireEnemy(enemy, true); fireEnemyOffset(enemy, -170f); fireEnemyOffset(enemy, 170f) }
                2 -> repeat(5) { i -> fireEnemyOffset(enemy, (i - 2) * 110f) }
                else -> fireEnemy(enemy, true)
            }
            enemy.lastShot = now
        }
    }

    private fun fireVolley(now: Long) {
        val damageBase = weaponLevel + if (now < overdriveUntil) 2 else 0
        when (weapon) {
            Weapon.LASER -> {
                bullets.add(Bullet(playerX, playerY - 56f, 0f, -900f, damageBase))
                if (weaponLevel >= 4) bullets.add(Bullet(playerX + 18f, playerY - 48f, 0f, -900f, damageBase))
            }
            Weapon.SPREAD -> {
                val extra = if (weaponLevel >= 3) 2 else 0
                bullets.add(Bullet(playerX, playerY - 54f, 0f, -830f, damageBase))
                bullets.add(Bullet(playerX, playerY - 48f, -230f, -790f, damageBase))
                bullets.add(Bullet(playerX, playerY - 48f, 230f, -790f, damageBase))
                if (extra > 0 || now < spreadUntil) {
                    bullets.add(Bullet(playerX, playerY - 42f, -380f, -720f, damageBase))
                    bullets.add(Bullet(playerX, playerY - 42f, 380f, -720f, damageBase))
                }
            }
            Weapon.PLASMA -> {
                val damage = damageBase * 2 + 1
                bullets.add(Bullet(playerX, playerY - 58f, 0f, -700f, damage))
                if (weaponLevel >= 4 || now < spreadUntil) {
                    bullets.add(Bullet(playerX - 14f, playerY - 46f, -120f, -680f, damage))
                    bullets.add(Bullet(playerX + 14f, playerY - 46f, 120f, -680f, damage))
                }
            }
        }
        playTone(ToneGenerator.TONE_PROP_BEEP, 28)
    }

    private fun fireEnemy(enemy: Enemy, aimed: Boolean) {
        var vx = 0f; var vy = 360f + level * 8f
        if (aimed) {
            val dx = playerX - enemy.x; val dy = playerY - enemy.y
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val speed = 420f + level * 6f
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
            level >= 4 && roll < 18 -> EnemyType.GUNNER
            level >= 3 && roll < 38 -> EnemyType.ZIGZAG
            level >= 2 && roll < 60 -> EnemyType.TANK
            else -> EnemyType.SCOUT
        }
        val hpBoost = when (difficulty) { Difficulty.EASY -> 0; Difficulty.NORMAL -> level / 7; Difficulty.HARD -> 1 + level / 5 }
        val radius: Float; val speed: Float; val hp: Int
        when (type) {
            EnemyType.SCOUT -> { radius = Random.nextInt(20, 28).toFloat(); speed = 160f + level * 9f; hp = 1 + hpBoost }
            EnemyType.TANK -> { radius = Random.nextInt(30, 39).toFloat(); speed = 95f + level * 7f; hp = 4 + level / 4 + hpBoost }
            EnemyType.ZIGZAG -> { radius = Random.nextInt(22, 31).toFloat(); speed = 130f + level * 8f; hp = 2 + level / 6 + hpBoost }
            EnemyType.GUNNER -> { radius = Random.nextInt(25, 34).toFloat(); speed = 105f + level * 6f; hp = 3 + level / 5 + hpBoost }
            EnemyType.BOSS -> return
        }
        enemies.add(Enemy(Random.nextFloat() * (width - radius * 2f) + radius, -radius, radius, speed, hp, hp, type))
    }

    private fun spawnBoss() {
        val multiplier = when (difficulty) { Difficulty.EASY -> .85f; Difficulty.NORMAL -> 1f; Difficulty.HARD -> 1.25f }
        val hp = ((42 + level * 7) * multiplier).toInt()
        enemies.clear(); bullets.removeAll { it.enemy }
        enemies.add(Enemy(width / 2f, height * .17f, width * .15f, 0f, hp, hp, EnemyType.BOSS, bossStyle = (level / 5 - 1) % 3))
        playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 280)
        vibrate(90)
    }

    private fun handleBulletHits() {
        val playerBullets = bullets.filter { !it.enemy }
        val deadBullets = mutableSetOf<Bullet>(); val deadEnemies = mutableSetOf<Enemy>()
        for (bullet in playerBullets) for (enemy in enemies) {
            val hitRadius = if (enemy.type == EnemyType.BOSS) enemy.r * 1.38f else enemy.r + 9f
            if (distance(bullet.x, bullet.y, enemy.x, enemy.y) < hitRadius) {
                deadBullets.add(bullet); enemy.hp -= bullet.damage
                if (enemy.hp <= 0 && enemy !in deadEnemies) {
                    deadEnemies.add(enemy)
                    onEnemyKilled(enemy)
                }
                break
            }
        }
        bullets.removeAll(deadBullets); enemies.removeAll(deadEnemies)
    }

    private fun onEnemyKilled(enemy: Enemy) {
        val nowKill = System.currentTimeMillis()
        combo = if (nowKill - lastKillAt < 1500L) (combo + 1).coerceAtMost(8) else 1
        lastKillAt = nowKill
        kills++
        val basePoints = when (enemy.type) { EnemyType.SCOUT -> 12; EnemyType.TANK -> 28; EnemyType.ZIGZAG -> 22; EnemyType.GUNNER -> 32; EnemyType.BOSS -> 500 }
        score += basePoints * combo
        val earned = when (enemy.type) { EnemyType.BOSS -> 40; EnemyType.TANK, EnemyType.GUNNER -> 3; else -> 1 }
        runCredits += earned
        missionProgress++
        makeExplosion(enemy.x, enemy.y, if (enemy.type == EnemyType.BOSS) 58 else 17)
        if (enemy.type == EnemyType.BOSS) {
            bossesKilled++
            health = (health + 35).coerceAtMost(maxHealth)
            spawnGuaranteedPowerUp(enemy.x, enemy.y)
            playTone(ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE, 350)
            vibrate(120)
        } else {
            maybeDropPowerUp(enemy.x, enemy.y)
            playTone(ToneGenerator.TONE_PROP_ACK, 42)
        }
        if (!missionComplete && missionProgress >= missionTarget()) {
            missionComplete = true
            runCredits += 100
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
        val hits = enemies.filter { enemy ->
            enemy.type != EnemyType.BOSS && (enemy.y > height + enemy.r || distance(enemy.x, enemy.y, playerX, playerY) < enemy.r + 34f)
        }
        if (hits.isEmpty()) return
        enemies.removeAll(hits.toSet())
        if (now < shieldUntil) { hits.forEach { makeExplosion(it.x, it.y, 8) }; return }
        val diffDamage = when (difficulty) { Difficulty.EASY -> .75f; Difficulty.NORMAL -> 1f; Difficulty.HARD -> 1.2f }
        val damage = hits.fold(0) { total, enemy -> total + when (enemy.type) {
            EnemyType.TANK -> 25; EnemyType.ZIGZAG -> 20; EnemyType.GUNNER -> 22; else -> 15
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
            PowerType.HEAL -> health = (health + 30).coerceAtMost(maxHealth)
            PowerType.OVERDRIVE -> overdriveUntil = maxOf(overdriveUntil, now + 8000L)
        }
        powerUps.removeAll(collected.toSet()); playTone(ToneGenerator.TONE_PROP_PROMPT, 110); vibrate(30)
    }

    private fun maybeDropPowerUp(x: Float, y: Float) {
        if (Random.nextInt(100) >= 20) return
        val values = PowerType.values(); powerUps.add(PowerUp(x, y, values[Random.nextInt(values.size)]))
    }

    private fun spawnGuaranteedPowerUp(x: Float, y: Float) {
        val values = PowerType.values(); powerUps.add(PowerUp(x, y, values[Random.nextInt(values.size)], 115f))
    }

    private fun makeExplosion(x: Float, y: Float, count: Int) {
        repeat(count) {
            val angle = Random.nextFloat() * 6.28318f; val speed = 70f + Random.nextFloat() * 290f
            particles.add(Particle(x, y, cos(angle) * speed, sin(angle) * speed, .55f + Random.nextFloat() * .45f, 3f + Random.nextFloat() * 7f))
        }
    }

    private fun missionTarget(): Int = 20 + (best / 1000).coerceAtMost(30)
    private fun missionText(): String = "MISSION: Destroy ${missionProgress.coerceAtMost(missionTarget())}/${missionTarget()} enemies"

    private fun endGame() {
        health = 0; shooting = false; state = State.GAME_OVER
        credits += runCredits
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
            .apply()
    }

    private fun upgradeWeapon() {
        if (weaponLevel >= 5) return
        val cost = weaponLevel * 180
        if (credits >= cost) { credits -= cost; weaponLevel++; saveProgress(); playTone(ToneGenerator.TONE_PROP_PROMPT, 120) }
        else playTone(ToneGenerator.TONE_PROP_NACK, 90)
    }

    private fun upgradeArmor() {
        if (armorLevel >= 5) return
        val cost = armorLevel * 160
        if (credits >= cost) { credits -= cost; armorLevel++; saveProgress(); playTone(ToneGenerator.TONE_PROP_PROMPT, 120) }
        else playTone(ToneGenerator.TONE_PROP_NACK, 90)
    }

    private fun nextShip() {
        if (selectedShip + 1 < unlockedShips) selectedShip++
        else if (unlockedShips < 3) {
            val cost = if (unlockedShips == 1) 800 else 1800
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
        val dx = ax - bx; val dy = ay - by; return sqrt(dx * dx + dy * dy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (state) {
                    State.MENU -> handleMenuTouch(event.x, event.y)
                    State.HANGAR -> handleHangarTouch(event.x, event.y)
                    State.PLAYING -> {
                        if (event.x > width - 115f && event.y < 110f) { state = State.PAUSED; shooting = false }
                        else { targetX = event.x; shooting = true }
                    }
                    State.PAUSED -> {
                        if (event.x < width * .25f && event.y < height * .25f) state = State.MENU
                        else { state = State.PLAYING; lastFrame = System.currentTimeMillis() }
                    }
                    State.GAME_OVER -> {
                        if (event.y in (height * .64f)..(height * .76f)) startGame()
                        else if (event.y in (height * .76f)..(height * .89f)) state = State.MENU
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
            y in (height * .37f)..(height * .49f) -> startGame()
            y in (height * .49f)..(height * .61f) -> state = State.HANGAR
            y in (height * .61f)..(height * .73f) -> cycleDifficulty()
            y > height * .90f && x < width * .50f -> { soundOn = !soundOn; saveProgress() }
            y > height * .90f -> { vibrationOn = !vibrationOn; saveProgress() }
        }
    }

    private fun handleHangarTouch(x: Float, y: Float) {
        when {
            y in (height * .455f)..(height * .545f) && x < width * .50f -> previousShip()
            y in (height * .455f)..(height * .545f) -> nextShip()
            y in (height * .56f)..(height * .66f) -> cycleWeapon()
            y in (height * .67f)..(height * .77f) -> upgradeWeapon()
            y in (height * .78f)..(height * .88f) -> upgradeArmor()
            y > height * .89f -> state = State.MENU
        }
    }

    private fun startGame() {
        score = 0; level = 1; combo = 1; kills = 0; bossesKilled = 0; runCredits = 0; missionProgress = 0; missionComplete = false
        maxHealth = 100 + (armorLevel - 1) * 15 + if (selectedShip == 2) 35 else 0
        health = maxHealth
        lastKillAt = 0L; shieldUntil = 0L; rapidUntil = 0L; spreadUntil = 0L; overdriveUntil = 0L; lastBossLevel = 0
        bullets.clear(); enemies.clear(); powerUps.clear(); particles.clear()
        playerX = width / 2f; playerY = height * .84f; targetX = playerX
        lastFrame = System.currentTimeMillis(); lastShot = 0L; lastSpawn = 0L; shooting = false
        state = State.PLAYING
    }
}
