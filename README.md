# Hollow Session — mod de horror ARG (NeoForge 1.21.1)

Não é um mod de monstro: é um mod que faz o jogador sentir que algo está errado com o próprio jogo.
Dura ~40 minutos de jogo ATIVO (o timer só anda enquanto o mundo está rodando) e termina num clímax.

## Como compilar
> Este código NÃO foi compilado nem testado (o ambiente em que foi escrito não tem rede).
> O caminho mais seguro: baixe o MDK oficial `NeoForgeMDKs/MDK-1.21.1-ModDevGradle`,
> copie a pasta `src/` deste projeto por cima da dele, ajuste o `mod_id` para `hollowsession`
> e rode `./gradlew build`. O .jar sai em `build/libs/`.
> Também dá para usar os arquivos Gradle deste zip (precisa gerar o wrapper: `gradle wrapper`).
> Se algum nome de API não bater com a sua versão 21.1.x, o compilador aponta a linha exata.

## Linha do tempo (SCALE em Timeline.java muda tudo: 1.5 = 60 min)
- Fase 1, 0–9 min: som de caverna, passos atrás, porta, "<você> entrou/saiu do jogo".
- Fase 2, 9–18 min: animais encaram o jogador, 1º sussurro com o nome do usuário do SO,
  **anomalia aos 12:00**, mensagem de morte falsa, pulso de escuridão.
- Fase 3, 18–30 min: anomalia reaparece, "página rasgada" no inventário, placa com o nome do jogador,
  câmera vira 180°, mensagem falsa escrita pelo próprio jogador, botão de sair vira "Salvar e sair?".
- Fase 4, 30–40 min: batimentos do Warden crescendo, mensagens mais diretas, "Killed <você>",
  botão de sair vira "Não.", anomalias mais frequentes.
- Clímax, 40:00: Blindness 255 + Slowness 255 infinitos, mensagem final em vermelho.
  Quando o jogador sair manualmente para o menu, a tela de título fica preta e sem botões.

## Regras técnicas do prompt original
- Nunca usa System.exit nem abre programas externos. Tudo acontece dentro da janela do Minecraft.
- Anomalia: 6 ArmorStands invisíveis, sem gravidade, Obsidiana na cabeça, vibrando e girando a cabeça,
  15 blocos atrás do jogador. Some na hora (Enderman teleport + Warden heartbeat) se o jogador olhar
  direto ou chegar a menos de 8 blocos. Criados com `new ArmorStand(...)` + lista + `addFreshEntity`
  (sem ID de entidade fixo). ArmorStands órfãos (ex.: após crash) são descartados ao carregar.
- Auto-save: `.opt_state_cache` na pasta do jogo a cada 5 s. Ao reentrar, o timer continua de onde parou.

## Nome de usuário do SO
Só é lido em singleplayer (servidor integrado), é exibido apenas no chat/placa/item locais e
não sai da sua máquina. Em servidor dedicado o mod usa o nick do jogo.

## Testes (precisa de cheats ligados)
`/hollow time` · `/hollow skip <min>` · `/hollow anomaly` · `/hollow climax` · `/hollow reset`

## COMO DESFAZER A "TELA DE TÍTULO PERMANENTEMENTE APAGADA"
Feche o jogo (X da janela) e apague o arquivo `.opt_state_cache` na pasta do Minecraft
(ou remova o .jar da pasta `mods`). Tudo volta ao normal.

## Observações
- Há uma "piscada" escura de ~0,15 s quando a anomalia some; apague o RenderGuiEvent em ClientEvents
  se isso for um problema para alguém sensível a flashes.
- Mensagens estão em en_us.json e pt_br.json (o jogo escolhe pelo idioma); edite à vontade.
- Pensado para singleplayer: o cliente lê o mesmo estado estático que o servidor integrado.
