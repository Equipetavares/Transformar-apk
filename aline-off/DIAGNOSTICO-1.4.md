# Diagnóstico 1.4
- Acesso especial do NotificationListenerService é distinto da permissão de avisos POST_NOTIFICATIONS.
- API 27+: verificação oficial NotificationManager.isNotificationListenerAccessGranted; fallback para versões antigas.
- API 30+: tela específica do componente com fallback para lista.
- Status em memória de conexão real, reconexão por API oficial e teste local voluntário de recepção.
- O teste não verifica a entrega de mensagens do WhatsApp. Verificar uma conversa individual, fora da tela de chat, com ação Responder e cinco segundos de espera.
- Não adiciona permissões de SMS, contatos, acessibilidade, internet nem contorna restrições do Play Protect.
- Mantém as duas respostas e a central de atendimentos; APK de depuração com chave do runner, instalação/atualização sujeita à assinatura e política do Android.
