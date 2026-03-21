package com.prisset.vtools.gui;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Function;

public class ValueSlider extends SliderWidget {

    private final double min;
    private final double max;
    private final Consumer<Double> onChange;
    private final Function<Double, Text> labelProvider;

    public ValueSlider(int x, int y, int width, int height,
                       double min, double max, double current,
                       Function<Double, Text> labelProvider,
                       Consumer<Double> onChange) {
        super(x, y, width, height, labelProvider.apply(current), toSlider(current, min, max));
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        this.labelProvider = labelProvider;
    }

    @Override
    protected void updateMessage() {
        setMessage(labelProvider.apply(getValue()));
    }

    @Override
    protected void applyValue() {
        onChange.accept(getValue());
    }

    public double getValue() {
        return min + (max - min) * this.value;
    }

    private static double toSlider(double current, double min, double max) {
        if (max <= min) return 0.0;
        return (current - min) / (max - min);
    }
}
