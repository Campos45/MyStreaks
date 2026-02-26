#MyStreaks 🔥
MyStreaks é uma aplicação nativa para Android, desenvolvida em Kotlin, desenhada para ser o teu hub pessoal de produtividade. Permite monitorizar a construção de hábitos diários, semanais e mensais (Streaks), bem como gerir tarefas únicas com sub-passos detalhados.

Com uma interface moderna, gamificação integrada, e funcionalidades avançadas de persistência de dados e processos em segundo plano, a MyStreaks ajuda-te a não quebrar a corrente!

✨ Funcionalidades Principais
🔄 Gestão de Hábitos (Streaks)
Múltiplas Frequências: Cria hábitos diários, semanais ou mensais.

Sistema de Fogo (🔥): Conta automaticamente os teus dias consecutivos de sucesso.

Visão de Calendário (Heatmap): Acompanha o teu progresso num calendário mensal visual (ao estilo do GitHub) que pinta a verde os dias de sucesso.

Categorias e Filtros (Tags): Organiza as tuas atividades por etiquetas (ex: 🏋️ Saúde, 💻 Trabalho) com sugestões automáticas e usa a lupa para filtrar o ecrã principal.

Organização Drag & Drop: Mantém o dedo pressionado e arrasta as atividades para as reordenares por prioridade.

Notificações Inteligentes e Personalizadas: Define uma hora e dia exatos para cada hábito. A aplicação desperta e envia-te um lembrete clicável para não falhares!

Motor de Validação Automático: Um serviço invisível corre em segundo plano verificando os prazos. Se falhares a meia-noite, a tua streak quebra e o teu recorde é guardado.

Arquivo: Desliza (Swipe) para arquivar atividades que já não queres monitorizar no dia a dia, sem perderes o seu histórico.

📝 Gestão de Tarefas (To-Do List)
Sub-passos Dinâmicos: Cria e edita tarefas complexas. Adiciona ou remove sub-passos dinamicamente em qualquer altura sem perderes o estado das checkboxes já marcadas.

Celebração de Conquistas (Confettis 🎉): Ao concluir todos os sub-passos, a tarefa é automaticamente finalizada com uma fantástica chuva de confettis no ecrã!

Histórico de Vitórias: As tarefas são movidas para o ecrã de "Concluídas", registando o dia e hora exatos em que foram terminadas.

🏆 Gamificação (Sala de Troféus)
Sistema de Conquistas: A aplicação analisa o teu histórico e desbloqueia medalhas automaticamente.

Medalhas de Consistência: Alcança marcas como 7, 30, 100 ou até 365 dias seguidos para ganhares troféus de resiliência.

Medalhas Especiais: Desafios escondidos como "O Madrugador" (completar antes das 8h00) ou "Fim de Semana Épico".

📊 Sistema de Backups ("Máquina do Tempo") e Logs
Backup e Restauro em JSON: Exporta uma cópia exata de toda a tua aplicação (Tarefas, Streaks e Histórico) para um ficheiro seguro. Se mudares de telemóvel ou apagares algo por engano, podes restaurar a base de dados instantaneamente!

Auditoria e TXT: A aplicação regista silenciosamente as tuas ações (criar, editar, concluir). Exporta tudo para um ficheiro .txt como um diário de bordo nativo.

📱 Widget de Ecrã Inicial
Acompanha o teu progresso sem abrir a app com um Widget interativo e redimensionável.

Atualizações otimizadas em segundo plano para evitar bloqueios no ecrã principal (Deadlock prevention).

🎨 Design e UX
Interface limpa, moderna e focada em cartões (Material Design 3).

Animações ricas (integração de Lottie para feedback visual).

Empty States: Ecrãs amigáveis com dicas visuais caso não tenhas atividades listadas.

Feedback de cores inteligente para diferentes frequências de atividades e estado das medalhas.

🛠️ Tecnologias e Arquitetura
Este projeto foi construído seguindo rigorosamente as melhores práticas de desenvolvimento nativo para Android:

Linguagem: Kotlin

Arquitetura: MVVM (Model-View-ViewModel) com Repositories.

Base de Dados: Room Database (com TypeConverters e Serialização/Desserialização via Gson para listas complexas e backups).

Processos Assíncronos: Coroutines & Flow.

Trabalho em Background: WorkManager (para verificações de tempo periódicas e reset de hábitos).

Alarmes Exatos: AlarmManager e BroadcastReceivers para notificações agendadas de forma precisa (compatível com as regras restritas do Android 12+).

Notificações: NotificationManager com PendingIntents e canais prioritários.

Interface & Animações: XML Layouts, ViewBinding, Material Components, ItemTouchHelper (Drag & Drop e Swipes) e biblioteca Lottie (Airbnb).

Armazenamento de Ficheiros: Storage Access Framework (SAF) para escrita e leitura de JSON/TXT.

Widgets: AppWidgetProvider & RemoteViewsService rodando em Threads dedicadas.
