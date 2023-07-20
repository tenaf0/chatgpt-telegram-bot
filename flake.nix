{
  description = "A very basic flake";

  outputs = { self, nixpkgs }:
    let
      pkgs = nixpkgs.legacyPackages.x86_64-linux.pkgs;
      charset-normalizer = with pkgs.python3Packages; toPythonModule (pkgs.python3Packages.charset-normalizer.overrideAttrs (old: rec {
                                                       version = "3.1.0";
                                                       src = pkgs.fetchFromGitHub {
                                                             owner = "Ousret";
                                                             repo = "charset_normalizer";
                                                             rev = "refs/tags/${version}";
                                                             hash = "sha256-uJSJFy4jD0CxpNaczBXDMGmu2M+G6Dk06pYO6q4asCY=";
                                                           };
                                                     }));
      htmldate = with pkgs.python3Packages; buildPythonPackage {
                                      pname = "htmldate";
                                      version = "1.4.3";
                                      src = fetchPypi {
                                        pname = "htmldate";
                                        version = "1.4.3";
                                        sha256 = "sha256-7FDwhLmX/fayb4wxRH5XifTetx/mk0LNodevDJ+R4Bs=";
                                      };

                                      propagatedBuildInputs = [ charset-normalizer python-dateutil dateparser lxml urllib3 ];
                                      doCheck = false;
                                  };
      courlan = with pkgs.python3Packages; buildPythonPackage {
                                pname = "courlan";
                                version = "0.9.3";
                                src = fetchPypi {
                                  pname = "courlan";
                                  version = "0.9.3";
                                  sha256 = "sha256-fm3Sp+V4U7lNuWVu4XabjNrMu0mCNGiRxCM4Mp1+CVg=";
                                };

                                propagatedBuildInputs = [ tld urllib3 langcodes ];
                                doCheck = false;
                            };
      jusText = with pkgs.python3Packages; buildPythonPackage {
                                      pname = "jusText";
                                      version = "3.0.0";
                                      src = fetchPypi {
                                        pname = "jusText";
                                        version = "3.0.0";
                                        sha256 = "sha256-dkDiSCGHlfa+ZfbDX+aXMloygPy0Z10VJbzf8rhvqt8=";
                                      };

                                      propagatedBuildInputs = [ lxml htmldate ];
                                      doCheck = false;
                                  };
      trafilatura = with pkgs.python3Packages; buildPythonPackage {
                          pname = "trafilatura";
                          version = "1.6.1";
                          src = fetchPypi {
                            pname = "trafilatura";
                            version = "1.6.1";
                            sha256 = "sha256-p3krA31iTQSrBfzOVWz+CLdx38jB2xSUx1CHlTDJoww=";
                          };

                          propagatedBuildInputs = [ courlan certifi jusText htmldate ];
                          doCheck = false;
                      };
      pythonEnv = pkgs.python3.withPackages(ps: [ trafilatura ]);
    in {
      devShells.x86_64-linux.default = with pkgs; mkShell {
        buildInputs = [ openjdk19 ];
        packages = [ pythonEnv ];
        };

      packages.x86_64-linux.trafilatura = trafilatura;
  };
}
