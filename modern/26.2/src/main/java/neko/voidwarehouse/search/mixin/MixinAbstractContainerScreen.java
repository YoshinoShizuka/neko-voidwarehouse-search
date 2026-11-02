package neko.voidwarehouse.search.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import neko.voidwarehouse.search.SearchClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给容器界面叠加「虚空仓库搜索框」。
 *
 * <h2>只叠加、不改动</h2>
 * <p>所有注入都是 TAIL / HEAD 吞键，不修改任何原版逻辑；搜索框只对
 * {@link ChestMenu}（大箱子家族，虚空仓库的界面就是一个 6 行大箱子）创建，
 * 其它容器界面（熔炉、酿造台……）一个字节都不碰。</p>
 *
 * <h2>🔴 键盘吞噬：为什么 keyPressed 必须在 HEAD 整个吞掉</h2>
 * <p>26.3 的 {@code EditBox.keyPressed} 只消费编辑键（方向键 / 退格 / 全选 /
 * 复制粘贴），<b>数字键与字母键从它手里 fall through</b>（javap -c 实证：
 * 方法体最后是识别键返回 1、否则返回 0）。而容器界面对数字键的语义是
 * {@code SWAP} —— 「点着仓库格按 1-9 把快捷栏整组存入仓库」。也就是说：
 * 如果只在搜索框聚焦时放行原版路由，玩家在框里打 {@code td}（铁锭），
 * 中途按下的每个数字都会把快捷栏那一格<b>真的存进虚空仓库</b>。</p>
 *
 * <p>所以聚焦期间的按键必须在 HEAD 全部拦下：先交给 {@code box.keyPressed}
 * 处理编辑键，然后无条件 {@code cancel} —— 编辑键已被处理，其余键本来就
 * 不该有任何游戏内语义。字母 / 汉字走的是 {@code charTyped}（CharacterEvent），
 * 与按键路由无关，仍由原版 children 路由送进聚焦的输入框。</p>
 *
 * <p>ESC 例外处理：聚焦时 ESC = 退出搜索（不关界面），再按一次才关界面 ——
 * 与创造模式物品栏的搜索框行为一致。</p>
 *
 * <h2>渲染为什么要手动接线</h2>
 * <p>26.x 的容器界面 {@code extractRenderState} <b>不调用 super</b>
 * （javap -c 实证：只走 extractContents / extractCarriedItem / extractTooltip），
 * Screen 的 renderables 出帧管线对容器界面不生效。所以这里在 TAIL 手动调
 * {@code box.extractRenderState(...)}，焦点光标、hint、选区全部由 EditBox 自己画。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen<T extends net.minecraft.world.inventory.AbstractContainerMenu> {

    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    protected int imageWidth;
    @Shadow
    protected int imageHeight;

    // 🔴 addRenderableWidget 不在这里 @Shadow：它声明在父类 Screen，而 @Shadow 只查
    // 目标类自己声明的成员（2026-11-01 真机崩溃实录）—— 父类成员走 AccessorScreen 的
    // @Invoker，见本类 onInit 里的调用。

    @Unique
    private static final int SEARCH_WIDTH = 140;
    @Unique
    private static final int SEARCH_HEIGHT = 16;

    /**
     * ESC 的扫描码（GLFW {@code GLFW_KEY_ESCAPE}）。
     *
     * <p>本工程编译类路径上没有 GLFW 头（loom 的非 remap 管线不自动带 lwjgl），
     * 而 GLFW 键码自 1.0 起从未变过 —— 256 是稳定常量，加依赖不值得。</p>
     */
    @Unique
    private static final int KEY_ESCAPE = 256;

    /** 本屏幕的搜索框；非大箱子类界面恒为 {@code null} */
    @Unique
    private EditBox voidwarehouseSearch$searchBox;

    // ------------------------------------------------------------------ 生命周期

    /**
     * init 尾声：大箱子类界面创建搜索框并探测服务端。
     * <b>框创建即隐藏</b>（{@code setVisible(false)}），服务端回目录才亮 ——
     * 普通大箱子永远不亮，界面上没有任何残迹。
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void voidwarehouseSearch$onInit(CallbackInfo ci) {
        if (!((Object) this.getMenu() instanceof ChestMenu)) {
            return;
        }
        EditBox box = new EditBox(Minecraft.getInstance().font,
                this.leftPos + (this.imageWidth - SEARCH_WIDTH) / 2,
                Math.max(2, this.topPos - SEARCH_HEIGHT - 2),
                SEARCH_WIDTH, SEARCH_HEIGHT,
                Component.literal("虚空仓库搜索"));
        box.setMaxLength(64);
        box.setVisible(false);
        box.setHint(Component.literal("搜索（中文/拼音/ID）"));
        box.setResponder(SearchClientState::onQueryChanged);
        // 加入 children（事件路由）+ renderables（narration）；渲染是我们自己出帧的
        ((AccessorScreen) (Object) this).voidwarehouseSearch$invokeAddRenderableWidget(box);
        this.voidwarehouseSearch$searchBox = box;
        SearchClientState.bind(box);
    }

    /** 屏幕关闭：清客户端状态（服务端的过滤跟着菜单消失，不需要清理包） */
    @Inject(method = "removed", at = @At("TAIL"))
    private void voidwarehouseSearch$onRemoved(CallbackInfo ci) {
        this.voidwarehouseSearch$searchBox = null;
        SearchClientState.clear();
    }

    /** 每 tick 推进 apply 的尾随防抖 */
    @Inject(method = "containerTick", at = @At("TAIL"))
    private void voidwarehouseSearch$onTick(CallbackInfo ci) {
        SearchClientState.tick();
    }

    // ------------------------------------------------------------------ 键盘

    /**
     * 搜索框聚焦时在 HEAD 吞掉全部按键（理由见类注释）。
     * ESC = 退出搜索框而不是关界面。
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void voidwarehouseSearch$onKeyPressed(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        EditBox box = this.voidwarehouseSearch$searchBox;
        if (box == null || !box.isFocused()) {
            return;
        }
        if (keyEvent.key() == KEY_ESCAPE) {
            this.voidwarehouseSearch$setSearchFocused(false);
            cir.setReturnValue(true);
            return;
        }
        // 编辑键交给输入框处理；其余键到此为止 —— 绝不能落到容器的
        // checkHotbarKeyPressed（数字键 = 把快捷栏整组存进仓库）
        box.keyPressed(keyEvent);
        cir.setReturnValue(true);
    }

    @Unique
    private void voidwarehouseSearch$setSearchFocused(boolean focused) {
        EditBox box = this.voidwarehouseSearch$searchBox;
        if (box == null) {
            return;
        }
        ((ContainerEventHandler) (Object) this).setFocused(focused ? box : null);
        if (box.isFocused() != focused) {
            box.setFocused(focused);
        }
    }

    // ------------------------------------------------------------------ 鼠标

    /**
     * 点击行为：<b>点框 = 聚焦 + 顺手刷新一次目录</b>（服务端限流之内）；
     * 点框外 = 退出搜索框（归还全部键盘语义给容器）。
     * 用 {@code @At("RETURN")} 而不是 TAIL：原版这个方法有多条返回路径。
     */
    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void voidwarehouseSearch$onMouseClicked(MouseButtonEvent event, boolean doubleClick,
                                                    CallbackInfoReturnable<Boolean> cir) {
        EditBox box = this.voidwarehouseSearch$searchBox;
        if (box == null) {
            return;
        }
        boolean inside = event.x() >= box.getX() && event.x() < box.getX() + box.getWidth()
                && event.y() >= box.getY() && event.y() < box.getY() + box.getHeight();
        if (inside && box.isVisible()) {
            // 原版路由已经点过 setFocused(box)；这里再对齐一次（幂等），
            // 顺带自愈「容器 focused 字段 == box 但 widget 标志已 false」的脏状态
            this.voidwarehouseSearch$setSearchFocused(true);
            SearchClientState.onSearchBoxClicked();
        } else if (box.isFocused()) {
            // 点框外：无论刚才那次点击是否被原版消费（点了槽位、点了框外空白），
            // 都退出搜索 —— 否则用户以为在点界面，键盘其实还在往框里打字
            this.voidwarehouseSearch$setSearchFocused(false);
        }
    }

    // ------------------------------------------------------------------ 渲染

    /** 容器界面的出帧管线不经过 renderables（见类注释），在 TAIL 手动画搜索框 */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void voidwarehouseSearch$onExtractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                                          float partialTick, CallbackInfo ci) {
        EditBox box = this.voidwarehouseSearch$searchBox;
        if (box != null && box.isVisible()) {
            box.extractRenderState(extractor, mouseX, mouseY, partialTick);
        }
    }

    /** {@code AbstractContainerScreen#getMenu}（26.3 实证为 public） */
    @Shadow
    public abstract T getMenu();
}
