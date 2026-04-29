# My UP - OBD2 Dashboard for VW UP TSI

Este projeto é uma versão customizada e otimizada do projeto original [ObdGraphs](https://github.com/tzebrowski/ObdGraphs), desenvolvido por Tomasz Żebrowski.

O **My UP** foi adaptado especificamente para o **VW UP TSI (brasileiro)**, com foco em telemetria precisa para motores de injeção direta e uma experiência premium no Android Auto em telas widescreen.

## 🚀 Principais Modificações e Melhorias

- **Identidade Visual:** Remodelagem completa da interface (Branding) de "My Giulia/Alfa Romeo" para "My UP/VW".
- **Monitor GTI (MIB3):** Inclusão de um novo modo de visualização inspirado no Monitor de Performance do VW Golf GTI (MIB3), com gauges de Turbo, Temperatura do Motor e Temperatura de Admissão.
- **Otimização para Widescreen:** Ajuste sistemático de layouts e renderização para telas de **1600x600** (comuns em centrais multimídia de 10.26"), com suporte a 3 colunas e correção de sobreposição de manômetros.
- **Telemetria VW UP TSI:**
    - Mapeamento de PIDs específicos para Injeção Direta.
    - Correção da pressão de combustível para **PSI** (usando PID de Alta Pressão/Rail).
    - Cálculo de Turbo em **Bar** (Boost Relativo: MAP - Baro).
    - Inclusão de sensores como **% de Etanol**, Temperatura do Catalisador e Posição do Pedal.
- **Android Auto:** Otimização de performance (FPS travado em 10 para estabilidade) e fontes aumentadas para legibilidade ao dirigir.

## 📄 Licença

Este projeto está licenciado sob a **Apache License, Version 2.0**.

```text
Copyright 2019-2026, Tomasz Żebrowski
Modifications Copyright 2026, My UP Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---
*Baseado no trabalho original de Tomasz Żebrowski. Customizações realizadas para a comunidade VW UP TSI.*