package com.tamalon.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.tamalon.app.R;
import com.tamalon.app.network.EstadisticasResponse;
import java.util.ArrayList;
import java.util.List;

public class BarChartCustomView extends View {
    private List<EstadisticasResponse.VisitaDia> dataList = new ArrayList<>();
    private Paint barPaint;
    private Paint textPaint;

    public BarChartCustomView(Context context) {
        super(context);
        init();
    }
    public BarChartCustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(getResources().getColor(R.color.tamalon_gold, null));
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(getResources().getColor(R.color.tamalon_ink, null));
        textPaint.setTextSize(28f);
    }

    public void setData(List<EstadisticasResponse.VisitaDia> data) {
        this.dataList = data;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dataList == null || dataList.isEmpty()) {
            canvas.drawText("Sin datos de visitas", 50, 100, textPaint);
            return;
        }

        int width = getWidth();
        int height = getHeight();
        int n = dataList.size();
        float barWidth = (float) width / (n * 2);
        float maxVisitas = 1f;
        for (EstadisticasResponse.VisitaDia d : dataList) {
            if (d.visitas > maxVisitas) maxVisitas = d.visitas;
        }

        for (int i = 0; i < n; i++) {
            EstadisticasResponse.VisitaDia d = dataList.get(i);
            float left = i * 2 * barWidth + barWidth / 2;
            float barHeight = ((float) d.visitas / maxVisitas) * (height - 100);
            float top = height - 50 - barHeight;
            float right = left + barWidth;
            float bottom = height - 50;

            canvas.drawRect(left, top, right, bottom, barPaint);
            canvas.drawText(d.fecha != null && d.fecha.length() >= 5 ? d.fecha.substring(5) : "", left, height - 20, textPaint);
        }
    }
}
