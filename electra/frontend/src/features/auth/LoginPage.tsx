import { useState } from "react";
import {
  Button,
  Center,
  Paper,
  Stack,
  Text,
  TextInput,
  PasswordInput,
  Title,
  Anchor,
  Alert,
} from "@mantine/core";
import { authApi } from "../../services/api/authApi";
import { session } from "../../services/storage/session";
export function LoginPage() {
  const [reset,setReset]=useState(false),[confirmation,setConfirmation]=useState(""),[message,setMessage]=useState(""),[error,setError]=useState("");
  const local=["localhost","127.0.0.1","[::1]","::1"].includes(window.location.hostname);
  const [email, setEmail] = useState(""),
    [password, setPassword] = useState(""),
    [name, setName] = useState(""),
    [register, setRegister] = useState(false),
    [busy, setBusy] = useState(false);
  return (
    <Center mih="100vh" p="md">
      <Paper p="xl" w={430} shadow="sm">
        <form
          noValidate
          onSubmit={async (e) => {
            e.preventDefault();
            setError("");setMessage("");
            const emailControl=e.currentTarget.querySelector<HTMLInputElement>('input[type="email"]');
            if(!emailControl?.validity.valid){setError("Confira o campo E-mail: informe o endereço cadastrado, sem vírgulas ou espaços.");return;}
            if(password.length===0){setError("Digite a senha que deseja usar.");return;}
            if(register && !reset && !name.trim()){setError("Informe seu nome.");return;}
            if(reset && password!==confirmation){setError("As senhas não coincidem.");return;}
            setBusy(true);
            try {
              if(reset){
                const result=await authApi.resetLocalPassword(email.trim(),password,confirmation);
                setReset(false);setRegister(false);setPassword("");setConfirmation("");setMessage(result.message);
                return;
              }
              session.save(
                register
                  ? await authApi.register(name, email.trim(), password)
                  : await authApi.login(email.trim(), password),
              );
            } catch(e) {
              setError(e instanceof Error?e.message:"Não foi possível concluir.");
            } finally {
              setBusy(false);
            }
          }}
        >
          <Stack>
            <Text fw={800} c="indigo" size="xl">
              QA Manager
            </Text>
            <Title order={2}>
              {reset ? "Redefinir senha local" : register ? "Criar acesso" : "Bem-vindo de volta"}
            </Title>
            <Text c="dimmed">Qualidade, execução e trabalho em equipe.</Text>
            {reset && <Text size="sm" c="dimmed">Informe o e-mail cadastrado e escolha uma nova senha. Recuperação simples para uso nesta máquina, sem envio de e-mail.</Text>}
            {message && <Alert color="green">{message}</Alert>}
            {error && <Alert color="red">{error}</Alert>}
            {register && !reset && (
              <TextInput
                label="Nome"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            )}
            <TextInput
              label="E-mail"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            <PasswordInput
              label={reset ? "Nova senha" : "Senha"}
              autoComplete={register || reset ? "new-password" : "current-password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              description={reset || register ? "Escolha livre: sem tamanho mínimo ou máximo e sem regra de caracteres." : undefined}
              required
            />
            {reset && <PasswordInput label="Confirmar nova senha" autoComplete="new-password" value={confirmation} onChange={e=>setConfirmation(e.currentTarget.value)} required />}
            <Button type="submit" loading={busy}>
              {reset ? "Salvar nova senha" : register ? "Criar conta" : "Entrar"}
            </Button>
            {local && !reset && <Anchor component="button" type="button" size="sm" disabled={busy} onClick={()=>{setReset(true);setMessage("");setError("");setPassword("");setConfirmation("");}}>Esqueci minha senha</Anchor>}
            <Anchor component="button" type="button" size="sm" disabled={busy} onClick={() => {if(reset){setReset(false);setRegister(false);}else setRegister(!register);setMessage("");setError("");setPassword("");setConfirmation("");}}>
              {reset ? "Voltar para entrar" : register ? "Já tenho acesso" : "Criar conta local"}
            </Anchor>
          </Stack>
        </form>
      </Paper>
    </Center>
  );
}
