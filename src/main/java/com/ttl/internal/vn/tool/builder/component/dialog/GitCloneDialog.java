package com.ttl.internal.vn.tool.builder.component.dialog;

import java.awt.BorderLayout;
import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.ttl.internal.vn.tool.builder.task.*;
import com.ttl.internal.vn.tool.builder.util.SwingGraphicUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;

import com.ttl.internal.vn.tool.builder.component.IDialog;
import com.ttl.internal.vn.tool.builder.component.Session;
import com.ttl.internal.vn.tool.builder.component.ValidationException;
import com.ttl.internal.vn.tool.builder.component.input.Button;
import com.ttl.internal.vn.tool.builder.component.input.FileField;
import com.ttl.internal.vn.tool.builder.component.input.InputGroup;
import com.ttl.internal.vn.tool.builder.component.input.PasswordField;
import com.ttl.internal.vn.tool.builder.component.input.ProgressBar;
import com.ttl.internal.vn.tool.builder.component.input.TextField;
import com.ttl.internal.vn.tool.builder.component.input.TextValidator;
import com.ttl.internal.vn.tool.builder.component.input.ValidatorError;
import com.ttl.internal.vn.tool.builder.util.GitUtil;
import com.ttl.internal.vn.tool.builder.util.GitUtil.CredentialEntry;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
// NOTE: Kill transport
public class GitCloneDialog extends JDialog implements IDialog {
    public GitCloneDialog(JFrame frame) {
        super();
        this.frame = frame;
        this.inputGroup = new InputGroup();
        this.inputGroup2 = new InputGroup();
        initUI();
        registerListeners();
    }

    private transient Logger logger = LogManager.getLogger(GitCloneDialog.class);
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField repoField;
    private Button cancelBtn;
    private Button cloneBtn;
    private FileField cloneFileField;
    private ProgressBar progressBar;
    private JFrame frame;
    private transient InputGroup inputGroup;
    private transient InputGroup inputGroup2;
    private transient GitCloneTask gitCloneTask;

    private static final int LABEL_LENGTH = 120;
    private static final int INPUT_LENGTH = 300;

    @Override
    public void initUI() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel mainPanel = new JPanel();
        GroupLayout mainGroupLayout = new GroupLayout(mainPanel);
        mainPanel.setLayout(mainGroupLayout);
        mainGroupLayout.setAutoCreateContainerGaps(true);
        mainGroupLayout.setAutoCreateGaps(true);

        List<TextValidator> validators = Arrays.asList(TextField.Validator.notBlank());
        usernameField = new TextField("Username", LABEL_LENGTH, INPUT_LENGTH, validators);
        passwordField = new PasswordField("Password", LABEL_LENGTH, INPUT_LENGTH, validators);
        repoField = new TextField("Repository", LABEL_LENGTH, INPUT_LENGTH, validators);
        cloneFileField = new FileField("Clone directory", LABEL_LENGTH, INPUT_LENGTH, JFileChooser.DIRECTORIES_ONLY,
                validators);
        inputGroup2.addInput(usernameField);
        inputGroup2.addInput(passwordField);
        inputGroup.addInput(repoField);
        inputGroup.addInput(cloneFileField);

        Group inputLayoutHGroup = mainGroupLayout.createParallelGroup()
                .addComponent(usernameField)
                .addComponent(passwordField)
                .addComponent(repoField)
                .addComponent(cloneFileField);
        Group inputLayoutVGroup = mainGroupLayout.createSequentialGroup()
                .addComponent(usernameField)
                .addComponent(passwordField)
                .addComponent(repoField)
                .addComponent(cloneFileField);

        cancelBtn = new Button("Cancel");
        cloneBtn = new Button("Clone");

        Group buttonLayoutHGroup = mainGroupLayout.createSequentialGroup()
                .addComponent(cloneBtn, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.PREFERRED_SIZE)
                .addComponent(cancelBtn, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.PREFERRED_SIZE);

        Group buttonLayoutVGroup = mainGroupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(cloneBtn, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.PREFERRED_SIZE)
                .addComponent(cancelBtn, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.PREFERRED_SIZE);

        progressBar = new ProgressBar();

        mainGroupLayout.setVerticalGroup(mainGroupLayout.createSequentialGroup()
                .addGroup(inputLayoutVGroup)
                .addGap(30)
                .addGroup(buttonLayoutVGroup));

        mainGroupLayout.setHorizontalGroup(mainGroupLayout.createParallelGroup()
                .addGroup(mainGroupLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
                        .addGroup(inputLayoutHGroup)
                        .addGroup(buttonLayoutHGroup)));

        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mainPanel, BorderLayout.NORTH);
        getContentPane().add(progressBar, BorderLayout.SOUTH);

        setTitle("Git Clone Dialog");
        setLocationRelativeTo(frame);
        setModalityType(ModalityType.APPLICATION_MODAL);
        setResizable(false);
        pack();
    }

    @Override
    public void refreshUI() {
        /** noop */
    }

    @Override
    public void registerListeners() {
        cloneBtn.addActionListener(e -> {
            try {
                cloneBtn.setEnabled(false);
                inputGroup.setEditable(false);
                inputGroup2.setEditable(false);
                List<ValidatorError> errors = inputGroup.validate();
                if (StringUtils.isNotBlank(usernameField.getText())
                        || StringUtils.isNotBlank(passwordField.getText())) {
                    errors.addAll(inputGroup2.validate());
                }
                if (errors.isEmpty()) {
                    String username = null;
                    String password = null;
                    if (Session.getInstance().isUseGitCredential()
                            && StringUtils.isBlank(usernameField.getText())) {
                        List<CredentialEntry> credentials = Session.getInstance()
                                .scanCredential(repoField.getText());
                        CredentialEntry chosenCredentialEntry = null;
                        for (CredentialEntry credentialEntry : credentials) {
                            String repo = GitUtil.getRepo(credentialEntry.getUrl());
                            if (chosenCredentialEntry == null || StringUtils.isNotBlank(repo)
                                    && StringUtils.isBlank(GitUtil.getRepo(chosenCredentialEntry.getUrl()))) {
                                chosenCredentialEntry = credentialEntry;
                            }
                        }
                        if (chosenCredentialEntry != null) {
                            username = chosenCredentialEntry.getUsername();
                            password = chosenCredentialEntry.getPassword();
                            usernameField.setText(username);
                            passwordField.setText(password);
                        }
                    } else {
                        username = usernameField.getText();
                        password = passwordField.getText();
                    }

                    GitUtil.checkLogin(username, password, repoField.getText());

                    if (StringUtils.isNotBlank(username)) {
                        Session.getInstance().setCredentialEntry(CredentialEntry.builder()
                                .username(username)
                                .password(password)
                                .url(repoField.getText())
                                .build());
                    }

                    this.gitCloneTask = new GitCloneTask(usernameField.getText(), passwordField.getText(),
                            repoField.getText(), cloneFileField.getSelectedFile());
                    gitCloneTask.setProgressMonitor(new GitCloneProgressMonitor(gitCloneTask, new PrintWriter(System.out)));
                    gitCloneTask.subscribe(new DefaultSubscriber<Task>() {
                        @Override
                        public void onNext(Task task) {
                            progressBar.setMaximum(100);
                            progressBar.setValue((int) (100. * task.percentage()));
                            progressBar.setStatus(task.explainTask() + " - " + (int) (task.percentage() * 100.) + "%");
                        }
                        
                        @Override
                        public void onComplete() {
                            Session.getInstance().setGitPassword(usernameField.getText());
                            Session.getInstance().setGitUsername(passwordField.getText());
                            dispose();
                        }
                    });
                    SwingGraphicUtil.supply(() -> {
                        try {
                            return gitCloneTask.start();
                        } catch (Throwable ex1) {
                            throw new RuntimeException(ex1);
                        }
                    });
                } else {
                    throw new ValidationException(errors.get(0));
                }
            } catch (Throwable ex) {
                handleException(ex);
            }
        });
        cancelBtn.addActionListener(e -> {
            Optional.ofNullable(gitCloneTask).ifPresent(GitCloneTask::cancel);
            dispose();
        });
    }

    @Override
    public int showDialog() {
        setVisible(true);
        return Optional.ofNullable(gitCloneTask).map(GitCloneTask::status).orElse(Task.TaskStatus.CANCEL.ordinal());
    }

    @Override
    public void handleException(Throwable e) {
        logger.error(e.getMessage(), e);
        inputGroup.setEditable(true);
        inputGroup2.setEditable(true);
        cloneBtn.setEnabled(true);
        JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        if (e instanceof ValidationException) {
            ((ValidationException) e).getError().getSrc().requestFocus();
        }
    }

    public File getClonedFolder() {
        return cloneFileField.getSelectedFile();
    }
}
