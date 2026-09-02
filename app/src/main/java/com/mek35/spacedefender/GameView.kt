package com.mek35.spacedefender

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    private enum class State { MENU, PLAYING, PAUSED, GAME_OVER }
    private enum class EnemyType { SCOUT, TANK, ZIGZAG, BOSS }
    private enum class PowerType { RAPID, SHIELD, SPREAD }

    private data class Bullet(var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = -820f, var damage: Int = 1)
    private data class Enemy(var x: Float, var y: Float, var r: Float, var speed: Float, var hp: Int, val maxHp: Int, val type: EnemyType, var phase: Float = Random.nextFloat() * 6.28f)
    private data class PowerUp(var x: Float, var y: Float, val type: PowerType, var speed: Float = 150f)
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var size: Float)

    private var state = State.MENU
    private var score = 0
    private var level = 1
    private var health = 100
    private var best = 0
    private var combo = 1
    private var lastKillAt = 0L
    private var shieldUntil = 0L
    private var rapidUntil = 0L
    private var spreadUntil = 0L
    private var lastBossLevel = 0

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

    private val prefs = context.getSharedPreferences("space_defender_v2", Context.MODE_PRIVATE)
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
    private val stars = MutableList(110) { Pair(Random.nextFloat(), Random.nextFloat()) }

    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val cyan = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65, 220, 255) }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 80, 100) }
    private val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 220, 80) }
    private val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 245, 150) }
    private val purple = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 100, 255) }
    private val orange = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 150, 70) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        best = prefs.getInt("best", 0)
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
            State.PLAYING -> {
                updateGame()
                drawHud(canvas); drawPlayer(canvas); drawBullets(canvas); drawEnemies(canvas)
                drawPowerUps(canvas); drawParticles(canvas); drawPauseButton(canvas)
                postInvalidateOnAnimation()
            }
            State.PAUSED -> {
                drawHud(canvas); drawPlayer(canvas); drawBullets(canvas); drawEnemies(canvas)
                drawPowerUps(canvas); drawParticles(canvas); drawPauseButton(canvas)
                drawOverlay(canvas, "PAUSED", "Tap anywhere to resume")
            }
            State.GAME_OVER -> {
                drawHud(canvas); drawPlayer(canvas); drawBullets(canvas); drawEnemies(canvas)
                drawPowerUps(canvas); drawParticles(canvas)
                drawOverlay(canvas, "GAME OVER", "Tap to play again")
            }
        }
    }

    private fun drawStars(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        stars.forEachIndexed { index, star ->
            p.alpha = 60 + (index * 19 % 180)
            val size = when { index % 13 == 0 -> 3.2f; index % 5 == 0 -> 2.2f; else -> 1.2f }
            canvas.drawCircle(star.first * width, star.second * height, size, p)
        }
    }

    private fun drawMenu(canvas: Canvas) {
        text.textSize = width * .10f; text.color = cyan.color
        canvas.drawText("SPACE DEFENDER V4", width / 2f, height * .23f, text)
        text.textSize = width * .046f; text.color = white.color
        canvas.drawText("BOSSES • POWER-UPS • LEVELS", width / 2f, height * .30f, text)
        drawButton(canvas, height * .52f, "PLAY")
        text.textSize = width * .035f; text.color = Color.LTGRAY
        canvas.drawText("Drag to move • Hold to fire", width / 2f, height * .70f, text)
        canvas.drawText("Collect power-ups and survive boss waves.", width / 2f, height * .75f, text)
        text.color = yellow.color
        canvas.drawText("BEST: $best", width / 2f, height * .84f, text)
    }

    private fun drawButton(canvas: Canvas, y: Float, label: String) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = cyan.color }
        canvas.drawRoundRect(width * .22f, y - 48f, width * .78f, y + 48f, 26f, 26f, p)
        text.textSize = width * .06f; text.color = white.color
        canvas.drawText(label, width / 2f, y + 20f, text)
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        val shade = Paint().apply { color = 0xC0000000.toInt() }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
        text.textSize = width * .088f; text.color = if (title == "GAME OVER") red.color else cyan.color
        canvas.drawText(title, width / 2f, height * .39f, text)
        text.textSize = width * .048f; text.color = white.color
        canvas.drawText("Score: $score   Level: $level", width / 2f, height * .47f, text)
        canvas.drawText(subtitle, width / 2f, height * .56f, text)
    }

    private fun drawHud(canvas: Canvas) {
        text.textAlign = Paint.Align.LEFT; text.textSize = width * .036f; text.color = white.color
        canvas.drawText("SCORE $score", 20f, 38f, text)
        canvas.drawText("LEVEL $level", 20f, 76f, text)
        if (combo > 1) {
            text.textAlign = Paint.Align.RIGHT; text.textSize = width * .030f; text.color = yellow.color
            canvas.drawText("COMBO x$combo", width - 20f, 76f, text)
            text.textAlign = Paint.Align.LEFT
        }

        val barLeft = width * .30f; val barTop = 18f; val barRight = width * .70f; val barBottom = 42f
        val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 45, 60) }
        canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, 12f, 12f, back)
        val fraction = health.coerceIn(0, 100) / 100f
        val healthColor = when { health > 60 -> green.color; health > 30 -> yellow.color; else -> red.color }
        val hpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = healthColor }
        canvas.drawRoundRect(barLeft, barTop, barLeft + (barRight - barLeft) * fraction, barBottom, 12f, 12f, hpPaint)
        text.textAlign = Paint.Align.CENTER; text.textSize = width * .027f; text.color = white.color
        canvas.drawText("HP $health", width / 2f, 39f, text)

        val now = System.currentTimeMillis()
        var powerText = ""
        if (now < shieldUntil) powerText += " SHIELD"
        if (now < rapidUntil) powerText += " RAPID"
        if (now < spreadUntil) powerText += " SPREAD"
        if (powerText.isNotBlank()) {
            text.textSize = width * .028f; text.color = cyan.color
            canvas.drawText(powerText.trim(), width / 2f, 76f, text)
        }
        text.textAlign = Paint.Align.CENTER
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
        val ship = Path()
        ship.moveTo(playerX, playerY - 48f); ship.lineTo(playerX - 34f, playerY + 29f)
        ship.lineTo(playerX - 10f, playerY + 17f); ship.lineTo(playerX, playerY + 7f)
        ship.lineTo(playerX + 10f, playerY + 17f); ship.lineTo(playerX + 34f, playerY + 29f); ship.close()
        canvas.drawPath(ship, cyan)
        val cockpit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(playerX, playerY - 10f, 9f, cockpit)
        canvas.drawCircle(playerX - 10f, playerY + 31f, 6f, yellow); canvas.drawCircle(playerX + 10f, playerY + 31f, 6f, yellow)
        if (System.currentTimeMillis() < shieldUntil) {
            val shield = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan.color; style = Paint.Style.STROKE; strokeWidth = 5f; alpha = 170 }
            canvas.drawCircle(playerX, playerY, 55f, shield)
        }
    }

    private fun drawBullets(canvas: Canvas) {
        bullets.forEach {
            val p = if (it.vx == 0f) yellow else cyan
            canvas.drawRoundRect(it.x - 4f, it.y - 16f, it.x + 4f, it.y + 16f, 5f, 5f, p)
        }
    }

    private fun drawEnemies(canvas: Canvas) {
        enemies.forEach { enemy ->
            when (enemy.type) {
                EnemyType.SCOUT -> {
                    canvas.drawCircle(enemy.x, enemy.y, enemy.r, red)
                    val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
                    canvas.drawCircle(enemy.x - enemy.r * .30f, enemy.y, enemy.r * .10f, eye)
                    canvas.drawCircle(enemy.x + enemy.r * .30f, enemy.y, enemy.r * .10f, eye)
                }
                EnemyType.TANK -> {
                    val p = Path(); p.moveTo(enemy.x, enemy.y - enemy.r); p.lineTo(enemy.x - enemy.r, enemy.y)
                    p.lineTo(enemy.x - enemy.r * .60f, enemy.y + enemy.r); p.lineTo(enemy.x + enemy.r * .60f, enemy.y + enemy.r)
                    p.lineTo(enemy.x + enemy.r, enemy.y); p.close(); canvas.drawPath(p, orange)
                }
                EnemyType.ZIGZAG -> {
                    val p = Path(); p.moveTo(enemy.x, enemy.y - enemy.r); p.lineTo(enemy.x - enemy.r, enemy.y + enemy.r)
                    p.lineTo(enemy.x, enemy.y + enemy.r * .45f); p.lineTo(enemy.x + enemy.r, enemy.y + enemy.r); p.close(); canvas.drawPath(p, purple)
                }
                EnemyType.BOSS -> {
                    val bossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(210, 55, 80) }
                    canvas.drawRoundRect(enemy.x - enemy.r * 1.4f, enemy.y - enemy.r * .65f, enemy.x + enemy.r * 1.4f, enemy.y + enemy.r * .65f, 24f, 24f, bossPaint)
                    canvas.drawCircle(enemy.x, enemy.y, enemy.r * .50f, orange)
                    val hpWidth = enemy.r * 2.3f; val hpFraction = enemy.hp.coerceAtLeast(0).toFloat() / enemy.maxHp.toFloat()
                    val hpBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY }
                    canvas.drawRect(enemy.x - hpWidth / 2f, enemy.y - enemy.r - 24f, enemy.x + hpWidth / 2f, enemy.y - enemy.r - 14f, hpBack)
                    canvas.drawRect(enemy.x - hpWidth / 2f, enemy.y - enemy.r - 24f, enemy.x - hpWidth / 2f + hpWidth * hpFraction, enemy.y - enemy.r - 14f, green)
                }
            }
        }
    }

    private fun drawPowerUps(canvas: Canvas) {
        powerUps.forEach { power ->
            val p = when (power.type) { PowerType.RAPID -> yellow; PowerType.SHIELD -> cyan; PowerType.SPREAD -> purple }
            canvas.drawCircle(power.x, power.y, 20f, p)
            text.textSize = 21f; text.color = Color.BLACK
            val label = when (power.type) { PowerType.RAPID -> "R"; PowerType.SHIELD -> "S"; PowerType.SPREAD -> "3" }
            canvas.drawText(label, power.x, power.y + 7f, text)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { particle ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (particle.life > .45f) yellow.color else red.color
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
        level = 1 + score / 250
        playerX += (targetX - playerX) * .25f
        playerX = playerX.coerceIn(42f, width - 42f)

        val fireDelay = if (now < rapidUntil) 80L else 180L
        if (shooting && now - lastShot > fireDelay) { fireVolley(now); lastShot = now }

        bullets.forEach { it.x += it.vx * delta; it.y += it.vy * delta }
        bullets.removeAll { it.y < -40f || it.x < -40f || it.x > width + 40f }

        if (level % 5 == 0 && level != lastBossLevel && enemies.none { it.type == EnemyType.BOSS }) {
            spawnBoss(); lastBossLevel = level
        }

        val bossAlive = enemies.any { it.type == EnemyType.BOSS }
        val spawnRate = (820L - level * 45L).coerceAtLeast(230L)
        if (!bossAlive && now - lastSpawn > spawnRate) { spawnEnemy(); lastSpawn = now }

        enemies.forEach { enemy ->
            when (enemy.type) {
                EnemyType.ZIGZAG -> {
                    enemy.phase += delta * 4.3f; enemy.x += sin(enemy.phase) * 170f * delta
                    enemy.y += enemy.speed * delta; enemy.x = enemy.x.coerceIn(enemy.r, width - enemy.r)
                }
                EnemyType.BOSS -> {
                    enemy.phase += delta * 1.7f
                    enemy.x = width / 2f + sin(enemy.phase) * width * .28f
                    enemy.y = height * .17f + cos(enemy.phase * .55f) * 18f
                }
                else -> enemy.y += enemy.speed * delta
            }
        }

        powerUps.forEach { it.y += it.speed * delta }; powerUps.removeAll { it.y > height + 50f }
        particles.forEach { it.x += it.vx * delta; it.y += it.vy * delta; it.life -= delta * 1.8f; it.size *= .985f }
        particles.removeAll { it.life <= 0f }

        handleBulletHits(); handlePlayerHits(now); handlePowerUps(now)
    }

    private fun fireVolley(now: Long) {
        if (now < spreadUntil) {
            bullets.add(Bullet(playerX, playerY - 48f, -230f, -800f))
            bullets.add(Bullet(playerX, playerY - 54f, 0f, -850f))
            bullets.add(Bullet(playerX, playerY - 48f, 230f, -800f))
        } else bullets.add(Bullet(playerX, playerY - 52f))
        if (now - lastShot > 100L) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
    }

    private fun spawnEnemy() {
        val roll = Random.nextInt(100)
        val type = when { level >= 3 && roll < 24 -> EnemyType.ZIGZAG; level >= 2 && roll < 48 -> EnemyType.TANK; else -> EnemyType.SCOUT }
        val radius: Float; val speed: Float; val hp: Int
        when (type) {
            EnemyType.SCOUT -> { radius = Random.nextInt(20, 28).toFloat(); speed = 155f + level * 10f + Random.nextFloat() * 70f; hp = 1 }
            EnemyType.TANK -> { radius = Random.nextInt(29, 38).toFloat(); speed = 92f + level * 7f; hp = 3 + level / 4 }
            EnemyType.ZIGZAG -> { radius = Random.nextInt(22, 30).toFloat(); speed = 125f + level * 9f; hp = 2 + level / 6 }
            EnemyType.BOSS -> return
        }
        enemies.add(Enemy(Random.nextFloat() * (width - radius * 2f) + radius, -radius, radius, speed, hp, hp, type))
    }

    private fun spawnBoss() {
        val hp = 25 + level * 5
        enemies.clear(); bullets.clear()
        enemies.add(Enemy(width / 2f, height * .17f, width * .15f, 0f, hp, hp, EnemyType.BOSS))
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
    }

    private fun handleBulletHits() {
        val deadBullets = mutableSetOf<Bullet>(); val deadEnemies = mutableSetOf<Enemy>()
        for (bullet in bullets) for (enemy in enemies) {
            val hitRadius = if (enemy.type == EnemyType.BOSS) enemy.r * 1.35f else enemy.r + 10f
            if (distance(bullet.x, bullet.y, enemy.x, enemy.y) < hitRadius) {
                deadBullets.add(bullet); enemy.hp -= bullet.damage
                if (enemy.hp <= 0) {
                    deadEnemies.add(enemy)
                    val nowKill = System.currentTimeMillis()
                    combo = if (nowKill - lastKillAt < 1600L) (combo + 1).coerceAtMost(5) else 1
                    lastKillAt = nowKill
                    val basePoints = when (enemy.type) { EnemyType.SCOUT -> 10; EnemyType.TANK -> 25; EnemyType.ZIGZAG -> 20; EnemyType.BOSS -> 300 }
                    score += basePoints * combo
                    makeExplosion(enemy.x, enemy.y, if (enemy.type == EnemyType.BOSS) 42 else 15)
                    if (enemy.type == EnemyType.BOSS) {
                        health = (health + 30).coerceAtMost(100); spawnGuaranteedPowerUp(enemy.x, enemy.y)
                        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE, 350)
                    } else { maybeDropPowerUp(enemy.x, enemy.y); tone.startTone(ToneGenerator.TONE_PROP_ACK, 45) }
                }
                break
            }
        }
        bullets.removeAll(deadBullets); enemies.removeAll(deadEnemies)
    }

    private fun handlePlayerHits(now: Long) {
        val hits = enemies.filter { enemy ->
            enemy.type != EnemyType.BOSS && (enemy.y > height + enemy.r || distance(enemy.x, enemy.y, playerX, playerY) < enemy.r + 31f)
        }
        if (hits.isEmpty()) return
        enemies.removeAll(hits.toSet())
        if (now < shieldUntil) { hits.forEach { makeExplosion(it.x, it.y, 8) }; return }health
        -= hits.fold(0) { total, enemy -> total + when (enemy.type) { EnemyType.TANK -> 25; EnemyType.ZIGZAG -> 20; else -> 15 } }
        combo = 1
        makeExplosion(playerX, playerY, 12); tone.startTone(ToneGenerator.TONE_PROP_NACK, 100)
        if (health <= 0) endGame()
    }

    private fun handlePowerUps(now: Long) {
        val collected = powerUps.filter { distance(it.x, it.y, playerX, playerY) < 48f }
        if (collected.isEmpty()) return
        for (power in collected) when (power.type) {
            PowerType.RAPID -> rapidUntil = maxOf(rapidUntil, now + 8000L)
            PowerType.SHIELD -> shieldUntil = maxOf(shieldUntil, now + 9000L)
            PowerType.SPREAD -> spreadUntil = maxOf(spreadUntil, now + 9000L)
        }
        powerUps.removeAll(collected.toSet()); tone.startTone(ToneGenerator.TONE_PROP_PROMPT, 120)
    }

    private fun maybeDropPowerUp(x: Float, y: Float) {
        if (Random.nextInt(100) >= 18) return
        val values = PowerType.values(); powerUps.add(PowerUp(x, y, values[Random.nextInt(values.size)]))
    }

    private fun spawnGuaranteedPowerUp(x: Float, y: Float) {
        val values = PowerType.values(); powerUps.add(PowerUp(x, y, values[Random.nextInt(values.size)], 120f))
    }

    private fun makeExplosion(x: Float, y: Float, count: Int) {
        repeat(count) {
            val angle = Random.nextFloat() * 6.28318f; val speed = 70f + Random.nextFloat() * 260f
            particles.add(Particle(x, y, cos(angle) * speed, sin(angle) * speed, .55f + Random.nextFloat() * .45f, 3f + Random.nextFloat() * 6f))
        }
    }

    private fun endGame() {
        health = 0; shooting = false; state = State.GAME_OVER
        if (score > best) { best = score; prefs.edit().putInt("best", best).apply() }
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by; return sqrt(dx * dx + dy * dy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (state) {
                    State.MENU -> if (event.y > height * .43f && event.y < height * .61f) startGame()
                    State.PLAYING -> {
                        if (event.x > width - 115f && event.y < 110f) { state = State.PAUSED; shooting = false }
                        else { targetX = event.x; shooting = true }
                    }
                    State.PAUSED -> { state = State.PLAYING; lastFrame = System.currentTimeMillis() }
                    State.GAME_OVER -> startGame()
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> { if (state == State.PLAYING) { targetX = event.x; shooting = true }; return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { shooting = false; return true }
        }
        return true
    }

    private fun startGame() {
        score = 0; level = 1; health = 100; combo = 1; lastKillAt = 0L; shieldUntil = 0L; rapidUntil = 0L; spreadUntil = 0L; lastBossLevel = 0
        bullets.clear(); enemies.clear(); powerUps.clear(); particles.clear()
        playerX = width / 2f; playerY = height * .84f; targetX = playerX
        lastFrame = System.currentTimeMillis(); lastShot = 0L; lastSpawn = 0L; shooting = false
        state = State.PLAYING
    }
}
