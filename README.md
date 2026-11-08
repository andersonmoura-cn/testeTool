# Xadrez RPG

Jogo de tabuleiro tático 8x8 para Android, inspirado em xadrez. Tem 4 classes de peça, cada uma com 2 habilidades.
Dá para jogar em **dois celulares na mesma rede Wi‑Fi** ou no **mesmo aparelho**, passando o celular.

## Instalar o APK

1. No celular, abra **Releases → "Xadrez RPG (última versão)"** do repositório no GitHub e baixe o arquivo **`XadrezRPG.apk`**.
   O APK é gerado automaticamente pelo GitHub Actions a cada push.
2. Abra o arquivo baixado. Se o Android pedir, permita **"Instalar apps desconhecidos"** para o navegador ou o gerenciador de arquivos.
3. Atualizações podem ser instaladas por cima da versão anterior, sem desinstalar.

## Jogar em rede local

1. Os dois celulares devem estar **no mesmo Wi‑Fi**.
2. No celular A, toque em **Criar partida**. A tela mostra o IP do aparelho.
3. No celular B, toque em **Entrar em partida**. A partida aparece sozinha na lista. Se não aparecer, digite o IP do celular A.
4. Cada jogador monta o próprio exército e a partida começa. Quem criou a partida é o **Azul** e começa jogando.

> Algumas redes (Wi‑Fi de visitantes, roteadores com "isolamento de clientes") bloqueiam a comunicação entre aparelhos.
> Se não conectar, tente o roteador de casa ou o **ancoragem/hotspot** de um dos celulares.

## Regras

- Tabuleiro 8x8. Cada jogador escolhe **até 8 peças**, em qualquer combinação, e as posiciona nas **suas 2 primeiras fileiras**.
- Na sua vez, você escolhe **uma peça**. Ela pode **mover e usar uma das suas 2 habilidades**, em qualquer ordem, e as duas ações são opcionais.
  A vez só passa quando você aperta **Encerrar turno**.
  As outras peças ficam sem habilidade, exceto os modos contínuos do Escudeiro.
- O movimento só vai para casas vazias, **sem captura**. A peça morre quando a vida chega a 0.
- **As habilidades de área também atingem suas próprias peças.**
- Distâncias contam casas em qualquer direção, incluindo a diagonal.
- Vence quem eliminar todas as peças do adversário, ou quando ele desiste.

| Classe | Vida | Movimento | Habilidade 1 | Habilidade 2 |
|---|---|---|---|---|
| 🔮 Conjurador | 1 | Teletransporte em L (cavalo) | **Choque do trovão:** 1 de dano em X (centro + 4 diagonais vizinhas), a até 3 casas | **Congelamento:** área 3x3 com centro a até 5 casas; as peças ficam paradas no próximo turno do dono, sem dano |
| 🏹 Atirador | 1 | Diagonal (bispo) | **Chuva de flechas:** 1 de dano em área 2x2 a até 3 casas | **Flecha perfurante:** 1 de dano em linha vertical/horizontal, atravessando até 2 peças |
| 🗡️ Espadachim | 2 | Reto (torre) | **Corte giratório:** 1 de dano em todas as 8 casas vizinhas | **Golpe divino:** 2 de dano em um alvo vizinho |
| 🛡️ Escudeiro | 3 | 1 casa em qualquer direção | **Proteger** (modo contínuo): toma o dano que mataria um aliado vizinho; não protege outro escudeiro, para evitar morte em cadeia | **Refletir** (modo contínuo): devolve ao atacante o dano de alvo único vindo de peça vizinha e não toma nada |

Os modos do Escudeiro ficam ativos até você escolher o outro modo num turno futuro.

## Estrutura do código

- `engine/`: motor de regras em Kotlin puro, com testes. É usado pelos dois aparelhos.
  - `Engine.kt`: aplica e valida ações e resolve dano, proteção, reflexo, congelamento e vitória.
  - `Rules.kt`: movimentos válidos, alvos e áreas das habilidades.
  - `Protocol.kt`: mensagens JSON da rede local.
- `app/`: app Android em Jetpack Compose.
  - `net/Session.kt`: partida local ou em rede. Usa TCP na porta 47123 e anúncio por broadcast UDP na porta 47124.
    O host é o juiz: valida cada ação e envia o estado completo aos dois aparelhos.
  - `ui/`: telas de menu, montagem do exército, tabuleiro e partida.

## Compilar

```bash
gradle -p engine test        # testes das regras (não precisa do Android SDK)
base64 -d app/debug.keystore.b64 > app/debug.keystore
gradle :app:assembleDebug    # APK (precisa do Android SDK)
```
