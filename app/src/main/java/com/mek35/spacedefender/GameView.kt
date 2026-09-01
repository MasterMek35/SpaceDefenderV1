package com.mek35.spacedefender

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    private enum class State {
        MENU, PLAYING, PAUSED, GAME_OVER
    }

    private data class Bullet(
        var x: Float,
        var y: Float
    )

    private data class Enemy(
        var x: Float,
        var y: Float,
        var r: Float,
        var speed: Float
    )

    private var state = State.MENU

    private var score = 0
    private var lives = 3
    private var best = 0

    private var playerX = 0f
    private var playerY = 0f
    private var targetX = 0f

    private var lastFrame = 0L
    private var lastShot = 0L
    private var lastSpawn = 0L

    private var shooting = false

    private val bullets = mutableListOf<Bullet>()
    private val enemies = mutableListOf<Enemy>()

    private val stars =
        MutableList(90) {
            Pair(
                Random.nextFloat(),
                Random.nextFloat()
            )
        }

    private val white =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }

    private val cyan =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(65, 220, 255)
        }

    private val red =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 80, 100)
        }

    private val yellow =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 220, 80)
        }

    private val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface =
                Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(Color.rgb(2, 3, 14))

        drawStars(canvas)

        when (state) {

            State.MENU -> {
                drawMenu(canvas)
            }

            State.PLAYING -> {
                updateGame()
                drawHud(canvas)
                drawPlayer(canvas)
                drawBullets(canvas)
                drawEnemies(canvas)
                postInvalidateOnAnimation()
            }

            State.PAUSED -> {
                drawHud(canvas)
                drawPlayer(canvas)
                drawBullets(canvas)
                drawEnemies(canvas)
                drawOverlay(
                    canvas,
                    "PAUSED",
                    "Tap to resume"
                )
            }

            State.GAME_OVER -> {
                drawHud(canvas)
                drawPlayer(canvas)
                drawBullets(canvas)
                drawEnemies(canvas)
                drawOverlay(
                    canvas,
                    "GAME OVER",
                    "Tap to play again"
                )
            }
        }
    }

    private fun drawStars(canvas: Canvas) {

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.WHITE

        stars.forEachIndexed { index, star ->

            paint.alpha =
                70 + (index * 17 % 150)

            val size =
                if (index % 5 == 0) 3f
                else 1.5f

            canvas.drawCircle(
                star.first * width,
                star.second * height,
                size,
                paint
            )
        }
    }

    private fun drawMenu(canvas: Canvas) {

        text.textSize = width * .105f
        text.color = cyan.color

        canvas.drawText(
            "SPACE DEFENDER",
            width / 2f,
            height * .25f,
            text
        )

        text.textSize = width * .052f
        text.color = white.color

        canvas.drawText(
            "ARCADE SHOOTER",
            width / 2f,
            height * .31f,
            text
        )

        drawButton(
            canvas,
            height * .53f,
            "PLAY"
        )

        text.textSize = width * .038f
        text.color = Color.LTGRAY

        canvas.drawText(
            "Move: drag anywhere • Fire: tap / hold",
            width / 2f,
            height * .72f,
            text
        )

        canvas.drawText(
            "Destroy enemies before they reach you.",
            width / 2f,
            height * .77f,
            text
        )

        if (best > 0) {

            text.color = yellow.color

            canvas.drawText(
                "BEST: $best",
                width / 2f,
                height * .86f,
                text
            )
        }
    }

    private fun drawButton(
        canvas: Canvas,
        y: Float,
        label: String
    ) {

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = cyan.color

        val left = width * .24f
        val right = width * .76f

        canvas.drawRoundRect(
            left,
            y - 45,
            right,
            y + 45,
            25f,
            25f,
            paint
        )

        text.textSize = width * .06f
        text.color = white.color

        canvas.drawText(
            label,
            width / 2f,
            y + 20,
            text
        )
    }

    private fun drawOverlay(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {

        val shade =
            Paint().apply {
                color = 0xB8000000.toInt()
            }

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            shade
        )

        text.textSize = width * .09f

        text.color =
            if (title == "GAME OVER")
                red.color
            else
                cyan.color

        canvas.drawText(
            title,
            width / 2f,
            height * .40f,
            text
        )

        text.textSize = width * .05f
        text.color = white.color

        canvas.drawText(
            "Score: $score",
            width / 2f,
            height * .48f,
            text
        )

        canvas.drawText(
            subtitle,
            width / 2f,
            height * .57f,
            text
        )
    }

    private fun drawHud(canvas: Canvas) {

        text.textAlign = Paint.Align.LEFT
        text.textSize = width * .042f
        text.color = white.color

        canvas.drawText(
            "SCORE $score",
            24f,
            42f,
            text
        )

        text.textAlign = Paint.Align.RIGHT

        canvas.drawText(
            "LIVES " + "♥".repeat(lives),
            width - 24f,
            42f,
            text
        )

        text.textAlign = Paint.Align.CENTER
    }

    private fun drawPlayer(canvas: Canvas) {

        if (playerX == 0f) {

            playerX = width / 2f
            playerY = height * .84f
            targetX = playerX
        }

        val path = Path()

        path.moveTo(
            playerX,
            playerY - 45
        )

        path.lineTo(
            playerX - 30,
            playerY + 28
        )

        path.lineTo(
            playerX,
            playerY + 15
        )

        path.lineTo(
            playerX + 30,
            playerY + 28
        )

        path.close()

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cyan.color
            }

        canvas.drawPath(path, paint)

        val flame =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = yellow.color
            }

        canvas.drawCircle(
            playerX,
            playerY + 30,
            8f,
            flame
        )
    }

    private fun drawBullets(canvas: Canvas) {

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = yellow.color
            }

        bullets.forEach {

            canvas.drawRoundRect(
                it.x - 3,
                it.y - 15,
                it.x + 3,
                it.y + 15,
                4f,
                4f,
                paint
            )
        }
    }

    private fun drawEnemies(canvas: Canvas) {

        enemies.forEach {

            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = red.color
                }

            canvas.drawCircle(
                it.x,
                it.y,
                it.r,
                paint
            )

            val eye =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                }

            canvas.drawCircle(
                it.x - it.r * .32f,
                it.y - it.r * .1f,
                it.r * .12f,
                eye
            )

            canvas.drawCircle(
                it.x + it.r * .32f,
                it.y - it.r * .1f,
                it.r * .12f,
                eye
            )
        }
    }

    private fun updateGame() {

        val now =
            System.currentTimeMillis()

        if (lastFrame == 0L)
            lastFrame = now

        val delta =
            ((now - lastFrame).coerceAtMost(40L)) / 1000f
        lastFrame = now

        playerX +=
            (targetX - playerX) *
            0.25f

        playerX =
            playerX.coerceIn(
                40f,
                width - 40f
            )

        if (
            shooting &&
            now - lastShot > 180
        ) {

            bullets.add(
                Bullet(
                    playerX,
                    playerY - 50
                )
            )

            lastShot = now
        }

        bullets.forEach {
            it.y -= 720f * delta
        }

        bullets.removeAll {
            it.y < -30
        }

        val spawnRate =
            (850L - score * 6L)
                .coerceAtLeast(260L)

        if (
            now - lastSpawn >
            spawnRate
        ) {

            val radius =
                Random.nextInt(
                    22,
                    34
                ).toFloat()

            enemies.add(
                Enemy(
                    Random.nextFloat() *
                        (width - radius * 2) +
                        radius,

                    -radius,

                    radius,

                    120f +
                        Random.nextFloat() *
                        90f +
                        score * 1.5f
                )
            )

            lastSpawn = now
        }

        enemies.forEach {
            it.y += it.speed * delta
        }

        val destroyedBullets =
            mutableSetOf<Bullet>()

        val destroyedEnemies =
            mutableSetOf<Enemy>()

        for (bullet in bullets) {

            for (enemy in enemies) {

                val dx =
                    bullet.x - enemy.x

                val dy =
                    bullet.y - enemy.y

                if (
                    dx * dx +
                    dy * dy <
                    (enemy.r + 10f) *
                    (enemy.r + 10f)
                ) {

                    destroyedBullets.add(
                        bullet
                    )

                    destroyedEnemies.add(
                        enemy
                    )

                    score += 10

                    break
                }
            }
        }

        bullets.removeAll(
            destroyedBullets
        )

        enemies.removeAll(
            destroyedEnemies
        )

        val hits =
            enemies.filter {

                it.y > height + it.r ||
                distance(
                    it.x,
                    it.y,
                    playerX,
                    playerY
                ) < it.r + 28
            }

        if (hits.isNotEmpty()) {

            enemies.removeAll(
                hits.toSet()
            )

            lives -= hits.size

            if (lives <= 0) {

                lives = 0

                best =
                    maxOf(
                        best,
                        score
                    )

                state =
                    State.GAME_OVER

                shooting = false
            }
        }
    }

    private fun distance(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {

        val dx = ax - bx
        val dy = ay - by

        return sqrt(
            dx * dx + dy * dy
        )
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                when (state) {

                    State.MENU -> {

                        if (
                            event.y >
                            height * .45f &&
                            event.y <
                            height * .63f
                        ) {
                            startGame()
                        }
                    }

                    State.PLAYING -> {

                        targetX =
                            event.x

                        shooting = true
                    }

                    State.PAUSED -> {

                        state =
                            State.PLAYING

                        lastFrame =
                            System.currentTimeMillis()
                    }

                    State.GAME_OVER -> {

                        startGame()
                    }
                }

                invalidate()

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                if (
                    state ==
                    State.PLAYING
                ) {

                    targetX =
                        event.x

                    shooting = true
                }

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                shooting = false

                return true
            }
        }

        return true
    }

    private fun startGame() {

        score = 0
        lives = 3

        bullets.clear()
        enemies.clear()

        playerX =
            width / 2f

        playerY =
            height * .84f

        targetX =
            playerX

        lastFrame =
            System.currentTimeMillis()

        lastShot = 0
        lastSpawn = 0

        shooting = false

        state =
            State.PLAYING
    }
}
