import React, { useState } from 'react';
import { Button, Form, Container, Row, Col, Card } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';
import api from '../../../api/axiosConfig';
import ToggleThemeButton from '../../components/Buttons/ToggleThemeButton';
import loginImage from '../../../assets/login.png';
import './Login.css';

const Login: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setErrorMessage('');

    try {
      // Ajuste 1: Verificação básica dos campos antes da requisição
      if (!email.trim() || !password) {
        throw new Error('Preencha todos os campos corretamente');
      }

      // Ajuste 2: Adicionado timeout e tratamento de erro específico
      const response = await api.post('/auth/login', {
        email: email.trim(), // Remove espaços em branco
        password
      }, {
        timeout: 180000 // Timeout de 8 segundos
      });

      console.log('Resposta do backend:', response.data);

      if (response.status === 200 && response.data.token) {
        const { token, user } = response.data;

        localStorage.setItem('token', token);
        console.log('Token armazenado:', token);

        if (user) {
          localStorage.setItem('user', JSON.stringify(user));
        }

        navigate('/dashboard', { state: { token } });
      } else {
        throw new Error('Resposta inválida do servidor');
      }
    } catch (error: any) {
      // Ajuste 3: Tratamento de erro mais preciso
      const errorMsg = error.response?.data?.message || 
                      error.message || 
                      'Erro na comunicação com o servidor';
      setErrorMessage(errorMsg);
      
      console.error('Detalhes do erro:', {
        error: error.response?.data || error.message,
        request: {
          url: error.config?.url,
          data: error.config?.data
        }
      });
    } finally {
      setIsLoading(false);
    }
  };


  return (
    <Container className="d-flex justify-content-center align-items-center min-vh-100">
      <Row className="w-100">
        <Col xs={12} md={6} lg={4} className="image-container">
          <Card className="shadow-lg rounded">
            <Card.Body>
              <h2 className="text-center mb-4">Login</h2>
              {errorMessage && (
                <div className="alert alert-danger text-center">{errorMessage}</div>
              )}
              <Form onSubmit={handleSubmit}>
                <Form.Group className="mb-3" controlId="formBasicEmail">
                  <Form.Label>Email</Form.Label>
                  <Form.Control
                    type="email"
                    placeholder="Digite seu email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    disabled={isLoading}
                  />
                </Form.Group>
                <Form.Group className="mb-3" controlId="formBasicPassword">
                  <Form.Label>Senha</Form.Label>
                  <Form.Control
                    type="password"
                    placeholder="Digite sua senha"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    disabled={isLoading}
                  />
                </Form.Group>
                <Button 
                  variant="primary" 
                  type="submit" 
                  className="w-100 mb-3"
                  disabled={isLoading}
                >
                  {isLoading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                      Carregando...
                    </>
                  ) : (
                    'Entrar'
                  )}
                </Button>
              </Form>
              <div className="text-center">
                <Link to="/forgot-password">Esqueceu a senha?</Link>
              </div>
              <div className="text-center mt-3">
                <ToggleThemeButton />
              </div>
            </Card.Body>
          </Card>
        </Col>
        <Col xs={12} md={6} className="d-none d-md-block">
          <div className="image-wrapper">
            <img src={loginImage} alt="Tela de login" className="img-fluid rounded" />
          </div>
        </Col>
      </Row>
    </Container>
  );
};

export default Login;