package com.prisset.vtools.survey;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Realistic tool switching with human-like delays.
 * Instant tool switch is a bot signature.
 */
public final class ToolManager {

    public enum ToolState {
        IDLE,       // no switch needed
        SELECTING,  // waiting reaction delay before switch
        SWITCHING,  // switch performed, waiting settle
        READY       // tool ready to use
    }

    private ToolState state = ToolState.IDLE;
    private int selectDelay;
    private int switchDelay;
    private BlockState targetBlock;

    public ToolManager() {}

    public ToolState getState() { return state; }
    public boolean isReady() { return state == ToolState.IDLE || state == ToolState.READY; }

    /**
     * Request a tool switch. Initiates with human reaction delay.
     */
    public void requestTool(ClientPlayerEntity player) {
        if (SlotHelper.isHoldingUsablePickaxe(player)) {
            state = ToolState.READY;
            return;
        }
        targetBlock = Blocks.STONE.getDefaultState();
        selectDelay = HumanTiming.toolSwitch();
        state = ToolState.SELECTING;
    }

    /**
     * Tick the tool switch process.
     * Returns true when tool is ready.
     */
    public boolean tick(ClientPlayerEntity player) {
        switch (state) {
            case IDLE, READY -> {
                return true;
            }

            case SELECTING -> {
                selectDelay--;
                if (selectDelay <= 0) {
                    // Perform the actual switch
                    if (!SlotHelper.selectBestPickaxe(player, targetBlock)) {
                        SlotHelper.pullPickaxeFromInventory(player);
                    }
                    state = ToolState.SWITCHING;
                    switchDelay = HumanTiming.gaussianDelay(2, 0.5f);
                }
                return false;
            }

            case SWITCHING -> {
                switchDelay--;
                if (switchDelay <= 0) {
                    state = ToolState.READY;
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Reset to idle state.
     */
    public void reset() {
        state = ToolState.IDLE;
    }
}
