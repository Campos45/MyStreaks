package pt.ipt.mystreaks.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class HexagonColorPickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val selectorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    private var selectorX = 0f
    private var selectorY = 0f

    var onColorChangeListener: ((String) -> Unit)? = null
    var currentColorHex: String = "#FFFFFF"

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = min(centerX, centerY) * 0.95f // 5% de margem

        // Começa com o seletor no meio
        selectorX = centerX
        selectorY = centerY

        createHexagon()
    }

    // Desenha as 6 pontas do Hexágono
    private fun createHexagon() {
        path.reset()
        for (i in 0..5) {
            val angle = Math.PI / 3 * i - Math.PI / 2 // Começa a ponta virada para cima
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Cortar o ecrã com a máscara do Hexágono! Tudo o que for pintado agora fica lá dentro.
        canvas.clipPath(path)

        // 2. Pintar o Espectro de Cores (Roda do Arco-Íris)
        val colors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
        paint.shader = SweepGradient(centerX, centerY, colors, null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // 3. Pintar a Luz no Centro (Branco no meio que desaparece nas pontas)
        paint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            Color.WHITE,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // 4. Desenhar o anel/mira onde o dedo está
        canvas.drawCircle(selectorX, selectorY, 20f, selectorShadowPaint) // Sombra preta para sobressair nas cores claras
        canvas.drawCircle(selectorX, selectorY, 20f, selectorPaint) // Anel branco
    }

    // O Motor de Toque e Arrasto
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Matemáticas para impedir o dedo de sair do hexágono/círculo
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                if (distance <= radius) {
                    selectorX = x
                    selectorY = y
                } else {
                    selectorX = centerX + (dx / distance) * radius
                    selectorY = centerY + (dy / distance) * radius
                }

                updateColor()
                invalidate() // Pede ao Android para redesenhar o ecrã!
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Transforma a posição (X,Y) do teu dedo numa cor HEX real!
    private fun updateColor() {
        val dx = selectorX - centerX
        val dy = selectorY - centerY
        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        // Calcular Hue (A que ângulo estamos?)
        var angle = atan2(dy.toDouble(), dx.toDouble())
        if (angle < 0) angle += 2 * Math.PI
        val hue = (angle * 180 / Math.PI).toFloat()

        // Calcular Saturação (Quão longe estamos do centro branco?)
        val saturation = min(1f, distance / radius)

        // Converter para Cor Real e notificar!
        val color = Color.HSVToColor(floatArrayOf(hue, saturation, 1f))
        currentColorHex = String.format("#%06X", 0xFFFFFF and color)
        onColorChangeListener?.invoke(currentColorHex)
    }
}