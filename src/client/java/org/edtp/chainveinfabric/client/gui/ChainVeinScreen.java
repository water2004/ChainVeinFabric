package org.edtp.chainveinfabric.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;
import org.edtp.chainveinfabric.Chainveinfabric;

public class ChainVeinScreen extends Screen {
    private Text chainVeinLabel;

    public ChainVeinScreen() {
        super(Text.of("Chain Vein Config"));
    }

    @Override
    protected void init() {
        super.init();
        this.chainVeinLabel = Text.translatable("options.chainveinfabric.chainVein");

        int buttonWidth = 60;
        int buttonX = this.width / 2 + 10;

        this.addDrawableChild(
                CyclingButtonWidget.onOffBuilder(Chainveinfabric.CONFIG.isChainVeinEnabled)
                        .omitKeyText() // This removes the "Label: " prefix and the colon
                        .build(
                                buttonX,
                                this.height / 4,
                                buttonWidth,
                                20,
                                Text.empty(),
                                (button, value) -> {
                                    Chainveinfabric.CONFIG.isChainVeinEnabled = value;
                                }
                        )
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderInGameBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFFFF);

        int labelX = this.width / 2 - this.textRenderer.getWidth(this.chainVeinLabel) - 20;
        int labelY = this.height / 4 + (20 - this.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(this.textRenderer, this.chainVeinLabel, labelX, labelY, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        Chainveinfabric.CONFIG.save();
        super.close();
    }
}


