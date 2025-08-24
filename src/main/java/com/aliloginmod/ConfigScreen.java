package com.aliloginmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ConfigScreen extends Screen {
    private final Screen parent;
    private EditBox sharedSecretField;
    private Button bypassToggleButton;
    private boolean tempAllowBypass;
    private String tempSharedSecret;
    
    public ConfigScreen(Screen parent) {
        super(Component.literal("AliLoginMod - Configurações"));
        this.parent = parent;
        this.tempAllowBypass = ClientConfig.allowClientBypass;
        this.tempSharedSecret = ClientConfig.sharedSecret;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Título
        int centerX = this.width / 2;
        int startY = 60;
        
        // Toggle para allowClientBypass
        this.bypassToggleButton = Button.builder(
                Component.literal("Auto-Login: " + (tempAllowBypass ? "§aAtivado" : "§cDesativado")),
                button -> {
                    tempAllowBypass = !tempAllowBypass;
                    button.setMessage(Component.literal("Auto-Login: " + (tempAllowBypass ? "§aAtivado" : "§cDesativado")));
                }
        ).bounds(centerX - 100, startY, 200, 20).build();
        this.addRenderableWidget(bypassToggleButton);
        
        // Campo para sharedSecret
        this.sharedSecretField = new EditBox(this.font, centerX - 100, startY + 40, 200, 20, Component.literal("Chave Secreta"));
        this.sharedSecretField.setValue(tempSharedSecret);
        this.sharedSecretField.setMaxLength(100);
        this.addRenderableWidget(sharedSecretField);
        
        // Botões com mais espaço (aumentei de +80 para +120)
        // Botão Salvar
        this.addRenderableWidget(Button.builder(
                Component.literal("§aSalvar"),
                button -> {
                    saveConfig();
                    this.minecraft.setScreen(parent);
                }
        ).bounds(centerX - 60, startY + 120, 50, 20).build()); // ← +120 em vez de +80
        
        // Botão Cancelar
        this.addRenderableWidget(Button.builder(
                Component.literal("§cCancelar"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(centerX + 10, startY + 120, 50, 20).build()); // ← +120 em vez de +80
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Título
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        // Labels
        guiGraphics.drawString(this.font, "Ativar Auto-Login:", this.width / 2 - 100, 50, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Chave Secreta:", this.width / 2 - 100, 90, 0xFFFFFF);
        
        // Descrições com mais espaço
        guiGraphics.drawCenteredString(this.font, "§7Configure o auto-login para servidores compatíveis", this.width / 2, 130, 0x888888);
        guiGraphics.drawCenteredString(this.font, "§7A chave secreta deve ser igual à do servidor", this.width / 2, 145, 0x888888);
        // ← Agora há mais espaço entre as descrições (linha 145) e os botões (linha 180)
    }
    
    private void saveConfig() {
        tempSharedSecret = sharedSecretField.getValue();
        
        // Salvar nas configurações
        ClientConfig.ALLOW_CLIENT_BYPASS.set(tempAllowBypass);
        ClientConfig.SHARED_SECRET.set(tempSharedSecret);
        
        // Atualizar valores estáticos
        ClientConfig.allowClientBypass = tempAllowBypass;
        ClientConfig.sharedSecret = tempSharedSecret;
        
        // Salvar arquivo de configuração
        ClientConfig.SPEC.save();
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}