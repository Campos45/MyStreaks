# MyStreaks
MyStreaks 🔥
MyStreaks é uma aplicação nativa para Android, desenvolvida em Kotlin, desenhada para ser o teu hub pessoal de produtividade. Permite monitorizar a construção de hábitos diários, semanais e mensais (Streaks), bem como gerir tarefas únicas com sub-passos detalhados.

Com uma interface moderna e funcionalidades avançadas de persistência de dados e processos em segundo plano, a MyStreaks ajuda-te a não quebrar a corrente!

✨ Funcionalidades Principais
🔄 Gestão de Hábitos (Streaks)
Múltiplas Frequências: Cria hábitos diários, semanais ou mensais.

Sistema de Fogo (🔥): Conta automaticamente os teus dias consecutivos de sucesso.

Motor de Validação Automático: Um serviço invisível corre em segundo plano verificando os prazos. Se falhares um prazo (ex: não marcar a tarefa diária até à meia-noite), a tua streak é quebrada e volta a zero automaticamente.

Notificações Inteligentes: Recebe alertas locais para não te esqueceres de marcar as tuas atividades antes do prazo terminar.

Histórico de Recordes: Se quebrares uma streak, a app guarda o teu recorde (data de início, fim e máximo alcançado) para que possas sempre tentar superar-te!

Arquivo: Desliza (Swipe) para arquivar atividades que já não queres monitorizar no dia a dia, sem perderes o seu histórico.

📝 Gestão de Tarefas (To-Do List)
Tarefas Simples ou Complexas: Cria tarefas únicas, com ou sem passos intermédios.

Cartões Expansíveis: Se uma tarefa tiver vários sub-passos, o cartão expande para mostrar as opções.

Automação Inteligente: Ao concluir todos os sub-passos, a tarefa principal marca-se como concluída automaticamente.

Histórico de Vitórias: As tarefas concluídas são movidas para um ecrã dedicado ("Concluídas"), registando o dia e hora exatos em que foram terminadas.

📊 Diário do Sistema (Logs) e Exportação
Auditoria Completa: A aplicação regista silenciosamente todas as tuas ações na base de dados (criar tarefas, completar streaks, arquivar, etc.) com timestamps exatos.

Ecrã de Logs: Uma interface dedicada para leres o histórico de tudo o que fizeste na app.

Exportação para TXT: Exporta o teu histórico completo para um ficheiro .txt para o armazenamento do telemóvel, utilizando a API nativa do Android (Storage Access Framework).

📱 Widget de Ecrã Inicial
Acompanha o teu progresso sem abrir a app!

Widget interativo que lista todas as tuas streaks ativas e o seu estado no dia atual.

Sincronização em tempo real com a base de dados.

🎨 Design e UX
Interface limpa, moderna e focada em cartões (Material Design).

Empty States: Ecrãs amigáveis com dicas visuais caso não tenhas atividades listadas.

Paleta de cores premium com feedback visual através de crachás (badges) de categorias.

🛠️ Tecnologias e Arquitetura
Este projeto foi construído seguindo as melhores práticas de desenvolvimento Android:

Linguagem: Kotlin

Arquitetura: MVVM (Model-View-ViewModel)

Base de Dados: Room Database (com TypeConverters e Gson para armazenamento de listas de objetos complexos)

Processos Assíncronos: Coroutines & Flow

Trabalho em Background: WorkManager (para verificações de tempo e resets à meia-noite)

Notificações: NotificationManager nativo compatível com Android 13+ (Tiramisu)

Interface: XML Layouts, ViewBinding, Material Components, ItemTouchHelper (Swipes)

Widgets: AppWidgetProvider & RemoteViewsService


